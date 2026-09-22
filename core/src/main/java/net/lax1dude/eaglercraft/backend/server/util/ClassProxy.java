/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.CacheBuilder
 *  com.google.common.cache.CacheLoader
 *  com.google.common.cache.LoadingCache
 *  com.google.common.collect.ImmutableMap
 *  com.google.common.collect.ImmutableMap$Builder
 */
package net.lax1dude.eaglercraft.backend.server.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import net.lax1dude.eaglercraft.backend.server.util.HashPair;
import net.lax1dude.eaglercraft.backend.server.util.Util;

public class ClassProxy<T> {
    private static final LoadingCache<HashPair<ClassLoader, Class<?>>, ClassProxy> loadingCache = CacheBuilder.newBuilder().build(new CacheLoader<HashPair<ClassLoader, Class<?>>, ClassProxy>(){

        public ClassProxy load(HashPair<ClassLoader, Class<?>> arg0) throws Exception {
            return new ClassProxy((ClassLoader)arg0.valueA, (Class)arg0.valueB);
        }
    });
    private final Method[] methods;
    private final Class<?> proxyClass;
    private final Map<Constructor<?>, Constructor<?>> ctor;

    private ClassProxy(ClassLoader loader, Class<?> parent) {
        try {
            Constructor<?>[] ctors = parent.getConstructors();
            if (ctors.length == 0) {
                throw new IllegalArgumentException("Class defines no public constructors");
            }
            this.methods = ClassProxy.getMethodsToOverride(parent.getMethods());
            this.proxyClass = ClassProxy.bindProxy(loader, parent, ctors, this.methods);
            ImmutableMap.Builder builder = ImmutableMap.builder();
            for (int i = 0; i < ctors.length; ++i) {
                Constructor<?> c = ctors[i];
                Parameter[] params = c.getParameters();
                int l = params.length;
                Class[] paramsClasses = new Class[2 + l];
                for (int j = 0; j < params.length; ++j) {
                    paramsClasses[j] = params[j].getType();
                }
                paramsClasses[l] = Method[].class;
                paramsClasses[l + 1] = InvocationHandler.class;
                builder.put(ctors[i], this.proxyClass.getConstructor(paramsClasses));
            }
            this.ctor = builder.build();
        }
        catch (Exception e) {
            throw Util.propagateReflectThrowable(e);
        }
    }

    private static Method[] getMethodsToOverride(Method[] meths) {
        ArrayList<Method> ret = new ArrayList<Method>();
        for (int i = 0; i < meths.length; ++i) {
            Method m = meths[i];
            if (m.getDeclaringClass() == Object.class || (m.getModifiers() & 0x10) != 0) continue;
            ret.add(m);
        }
        return ret.toArray(new Method[ret.size()]);
    }

    public T createProxy(Constructor<?> ctor, Object[] params, InvocationHandler invocationHandler) {
        Constructor<?> ctorImpl = this.ctor.get(ctor);
        if (ctorImpl == null) {
            throw new IllegalArgumentException("Unknown constructor: " + ctor.toString());
        }
        int j = params.length;
        Object[] params2 = new Object[j + 2];
        System.arraycopy(params, 0, params2, 0, j);
        params2[j] = this.methods;
        params2[j + 1] = invocationHandler;
        try {
            return (T)ctorImpl.newInstance(params2);
        }
        catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw Util.propagateReflectThrowable(e);
        }
    }

    public static <T> T createProxy(ClassLoader loader, Class<T> parent, InvocationHandler invocationHandler) {
        Constructor<T> ctor;
        try {
            ctor = parent.getConstructor(new Class[0]);
        }
        catch (NoSuchMethodException | SecurityException e) {
            throw Util.propagateReflectThrowable(e);
        }
        return ClassProxy.createProxy(loader, parent, ctor, new Object[0], invocationHandler);
    }

    public static <T> T createProxy(ClassLoader loader, Class<T> parent, Constructor<T> ctor, Object[] params, InvocationHandler invocationHandler) {
        ClassProxy<T> ret;
        try {
            ret = (ClassProxy<T>)loadingCache.get(new HashPair(loader, parent));
        }
        catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            throw new RuntimeException(t);
        }
        return ret.createProxy(ctor, params, invocationHandler);
    }

    public static <T> ClassProxy<T> bindProxy(ClassLoader loader, Class<T> parent) {
        try {
            return (ClassProxy<T>)loadingCache.get(new HashPair(loader, parent));
        }
        catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            throw new RuntimeException(t);
        }
    }

    private static Class<?> bindProxy(ClassLoader loader, Class<?> parent, Constructor<?>[] ctors, Method[] methods) throws Exception {
        MethodVisitor methodVisitor;
        Parameter[] params;
        String desc;
        int i;
        String parentName = Type.getInternalName(parent);
        String randomName = "EaglerClassProxy" + System.nanoTime();
        String proxyName = parentName + "/" + randomName;
        ClassWriter classWriter = new ClassWriter(1);
        classWriter.visit(52, 33, proxyName, null, parentName, new String[]{Type.getInternalName(IClassProxy.class)});
        classWriter.visitField(18, "meth", "[Ljava/lang/reflect/Method;", null, null).visitEnd();
        classWriter.visitField(18, "handler", "Ljava/lang/reflect/InvocationHandler;", null, null).visitEnd();
        for (i = 0; i < ctors.length; ++i) {
            Constructor<?> ctor = ctors[i];
            desc = Type.getConstructorDescriptor(ctor);
            if (!desc.endsWith(")V")) {
                throw new IllegalStateException();
            }
            String desc2 = desc.substring(0, desc.length() - 2) + "[Ljava/lang/reflect/Method;Ljava/lang/reflect/InvocationHandler;)V";
            params = ctor.getParameters();
            methodVisitor = classWriter.visitMethod(1, "<init>", desc2, null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitVarInsn(25, 0);
            for (int j = 0; j < params.length; ++j) {
                ClassProxy.loadParam(methodVisitor, j + 1, params[j].getType());
            }
            methodVisitor.visitMethodInsn(183, parentName, "<init>", desc, false);
            methodVisitor.visitVarInsn(25, 0);
            methodVisitor.visitVarInsn(25, params.length + 1);
            methodVisitor.visitFieldInsn(181, proxyName, "meth", "[Ljava/lang/reflect/Method;");
            methodVisitor.visitVarInsn(25, 0);
            methodVisitor.visitVarInsn(25, params.length + 2);
            methodVisitor.visitFieldInsn(181, proxyName, "handler", "Ljava/lang/reflect/InvocationHandler;");
            methodVisitor.visitInsn(177);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }
        for (i = 0; i < methods.length; ++i) {
            Method meth = methods[i];
            desc = Type.getMethodDescriptor(meth);
            methodVisitor = classWriter.visitMethod(1, meth.getName(), desc, null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitVarInsn(25, 0);
            methodVisitor.visitFieldInsn(180, proxyName, "handler", "Ljava/lang/reflect/InvocationHandler;");
            methodVisitor.visitVarInsn(25, 0);
            methodVisitor.visitVarInsn(25, 0);
            methodVisitor.visitFieldInsn(180, proxyName, "meth", "[Ljava/lang/reflect/Method;");
            ClassProxy.visitICONST(methodVisitor, i);
            methodVisitor.visitInsn(50);
            params = meth.getParameters();
            int k = params.length;
            if (k > 0) {
                ClassProxy.visitICONST(methodVisitor, k);
                methodVisitor.visitTypeInsn(189, "java/lang/Object");
                for (int j = 0; j < k; ++j) {
                    methodVisitor.visitInsn(89);
                    Parameter p = params[j];
                    ClassProxy.visitICONST(methodVisitor, j);
                    ClassProxy.loadParam(methodVisitor, 1 + j, p.getType());
                    ClassProxy.visitWrap(methodVisitor, p.getType());
                    methodVisitor.visitInsn(83);
                }
            } else {
                methodVisitor.visitInsn(3);
                methodVisitor.visitTypeInsn(189, "java/lang/Object");
            }
            methodVisitor.visitMethodInsn(185, "java/lang/reflect/InvocationHandler", "invoke", "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;", true);
            Class<?> ret = meth.getReturnType();
            ClassProxy.visitUnwrap(methodVisitor, ret);
            ClassProxy.visitReturn(methodVisitor, ret);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();
        String name = proxyName.replace('/', '.').replace('$', '.');
        return new ProxyClassLoader(loader, name, classWriter.toByteArray()).loadClass(name);
    }

    private static void loadParam(MethodVisitor methodVisitor, int j, Class<?> clz) {
        if (clz == Void.TYPE) {
            throw new IllegalArgumentException();
        }
        if (clz == Integer.TYPE || clz == Short.TYPE || clz == Byte.TYPE || clz == Boolean.TYPE) {
            methodVisitor.visitVarInsn(21, j);
        } else if (clz == Long.TYPE) {
            methodVisitor.visitVarInsn(22, j);
        } else if (clz == Float.TYPE) {
            methodVisitor.visitVarInsn(23, j);
        } else if (clz == Double.TYPE) {
            methodVisitor.visitVarInsn(24, j);
        } else {
            methodVisitor.visitVarInsn(25, j);
        }
    }

    private static void visitICONST(MethodVisitor methodVisitor, int i) {
        switch (i) {
            case 0: {
                methodVisitor.visitInsn(3);
                break;
            }
            case 1: {
                methodVisitor.visitInsn(4);
                break;
            }
            case 2: {
                methodVisitor.visitInsn(5);
                break;
            }
            case 3: {
                methodVisitor.visitInsn(6);
                break;
            }
            case 4: {
                methodVisitor.visitInsn(7);
                break;
            }
            case 5: {
                methodVisitor.visitInsn(8);
                break;
            }
            default: {
                if (i < -128 || i > 127) {
                    methodVisitor.visitIntInsn(17, i);
                    break;
                }
                if (i == -1) {
                    methodVisitor.visitInsn(2);
                    break;
                }
                methodVisitor.visitIntInsn(16, i);
            }
        }
    }

    private static void visitWrap(MethodVisitor methodVisitor, Class<?> clz) {
        if (clz == Void.TYPE) {
            throw new IllegalArgumentException();
        }
        if (clz == Integer.TYPE) {
            methodVisitor.visitMethodInsn(184, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (clz == Short.TYPE) {
            methodVisitor.visitMethodInsn(184, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
        } else if (clz == Byte.TYPE) {
            methodVisitor.visitMethodInsn(184, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        } else if (clz == Boolean.TYPE) {
            methodVisitor.visitMethodInsn(184, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        } else if (clz == Long.TYPE) {
            methodVisitor.visitMethodInsn(184, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        } else if (clz == Float.TYPE) {
            methodVisitor.visitMethodInsn(184, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
        } else if (clz == Double.TYPE) {
            methodVisitor.visitMethodInsn(184, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        }
    }

    private static void visitUnwrap(MethodVisitor methodVisitor, Class<?> clz) {
        if (clz == Void.TYPE) {
            methodVisitor.visitInsn(87);
        } else if (clz == Integer.TYPE) {
            methodVisitor.visitTypeInsn(192, "java/lang/Integer");
            methodVisitor.visitMethodInsn(182, "java/lang/Integer", "intValue", "()I", false);
        } else if (clz == Short.TYPE) {
            methodVisitor.visitTypeInsn(192, "java/lang/Short");
            methodVisitor.visitMethodInsn(182, "java/lang/Short", "shortValue", "()S", false);
        } else if (clz == Byte.TYPE) {
            methodVisitor.visitTypeInsn(192, "java/lang/Byte");
            methodVisitor.visitMethodInsn(182, "java/lang/Byte", "byteValue", "()B", false);
        } else if (clz == Boolean.TYPE) {
            methodVisitor.visitTypeInsn(192, "java/lang/Boolean");
            methodVisitor.visitMethodInsn(182, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else if (clz == Long.TYPE) {
            methodVisitor.visitTypeInsn(192, "java/lang/Long");
            methodVisitor.visitMethodInsn(182, "java/lang/Long", "longValue", "()J", false);
        } else if (clz == Float.TYPE) {
            methodVisitor.visitTypeInsn(192, "java/lang/Float");
            methodVisitor.visitMethodInsn(182, "java/lang/Float", "floatValue", "()F", false);
        } else if (clz == Double.TYPE) {
            methodVisitor.visitTypeInsn(192, "java/lang/Double");
            methodVisitor.visitMethodInsn(182, "java/lang/Double", "doubleValue", "()D", false);
        } else if (clz != Object.class) {
            methodVisitor.visitTypeInsn(192, Type.getInternalName(clz));
        }
    }

    private static void visitReturn(MethodVisitor methodVisitor, Class<?> clz) {
        if (clz == Void.TYPE) {
            methodVisitor.visitInsn(177);
        } else if (clz == Integer.TYPE || clz == Short.TYPE || clz == Byte.TYPE || clz == Boolean.TYPE) {
            methodVisitor.visitInsn(172);
        } else if (clz == Long.TYPE) {
            methodVisitor.visitInsn(173);
        } else if (clz == Float.TYPE) {
            methodVisitor.visitInsn(174);
        } else if (clz == Double.TYPE) {
            methodVisitor.visitInsn(175);
        } else {
            methodVisitor.visitInsn(176);
        }
    }

    public static interface IClassProxy {
    }

    public static class ProxyClassLoader
    extends ClassLoader {
        private final String name;
        private final byte[] data;

        protected ProxyClassLoader(ClassLoader parent, String name, byte[] data) {
            super(parent);
            this.name = name;
            this.data = data;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (this.name.equals(name)) {
                return super.defineClass(name, this.data, 0, this.data.length);
            }
            return super.findClass(name);
        }
    }
}

