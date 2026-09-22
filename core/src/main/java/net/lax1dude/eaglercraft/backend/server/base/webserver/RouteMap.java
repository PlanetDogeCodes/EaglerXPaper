/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 */
package net.lax1dude.eaglercraft.backend.server.base.webserver;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.api.EnumRequestMethod;

public class RouteMap<L, T> {
    public static final int numMeths = 6;
    private final RouteTreeNode<L, T> rootNode = new RouteTreeNode<L, T>(null);
    public static final List<EnumRequestMethod> allMethods = ImmutableList.of(EnumRequestMethod.GET, EnumRequestMethod.HEAD, EnumRequestMethod.PUT, EnumRequestMethod.DELETE, EnumRequestMethod.POST, EnumRequestMethod.PATCH);

    public boolean register(Iterator<CharSequence> tokens, boolean dir, L listener, int methId, T value) {
        RouteTreeNode path = this.rootNode;
        while (tokens.hasNext()) {
            path = path.getOrCreateChild(tokens.next().toString());
        }
        IRouteEndpoint<L, T> endpoint = path.getEndpoint(dir);
        if (listener == null) {
            if (endpoint == null) {
                path.setEndpoint(dir, new RouteEndpointAllListener(this.boostrapMethods(methId, value)));
                return true;
            }
            if (endpoint.allListener()) {
                if (methId != -1) {
                    IRouteMethods<T> method = endpoint.getForListener(null);
                    if (method instanceof RouteMethodPerMethod) {
                        return this.addMethod((RouteMethodPerMethod)method, methId, value);
                    }
                    return false;
                }
                return false;
            }
            return false;
        }
        if (endpoint == null) {
            RouteEndpointPerListener tmp1 = new RouteEndpointPerListener();
            tmp1.entries.put(listener, this.boostrapMethods(methId, value));
            path.setEndpoint(dir, tmp1);
            return true;
        }
        if (endpoint instanceof RouteEndpointPerListener) {
            RouteEndpointPerListener<L, T> tmp1 = (RouteEndpointPerListener<L, T>)endpoint;
            IRouteMethods method = tmp1.entries.get(listener);
            if (method != null) {
                if (methId != -1) {
                    if (method instanceof RouteMethodPerMethod) {
                        return this.addMethod((RouteMethodPerMethod)method, methId, value);
                    }
                    return false;
                }
                return false;
            }
            tmp1.entries.put(listener, this.boostrapMethods(methId, value));
            return true;
        }
        return false;
    }

    private IRouteMethods<T> boostrapMethods(int methId, T value) {
        if (methId != -1) {
            RouteMethodPerMethod tmp = new RouteMethodPerMethod();
            tmp.obj[methId] = value;
            ++tmp.count;
            return tmp;
        }
        return new RouteMethodAllMethods<T>(value);
    }

    private boolean addMethod(RouteMethodPerMethod<T> meth, int methId, T value) {
        if (((RouteMethodPerMethod)meth).obj[methId] == null) {
            ((RouteMethodPerMethod)meth).obj[methId] = value;
            ++((RouteMethodPerMethod)meth).count;
            return true;
        }
        return false;
    }

    public boolean remove(Iterator<CharSequence> tokens, boolean dir, L listener, int methId, T value) {
        RouteTreeNode<L, T> endpointNode = this.rootNode.find(tokens, dir);
        if (endpointNode == null) {
            return false;
        }
        IRouteEndpoint<L, T> endpoint = endpointNode.getEndpoint(dir);
        if (endpoint == null) {
            return false;
        }
        if (listener == null) {
            if (endpoint instanceof RouteEndpointAllListener) {
                RouteEndpointAllListener<L, T> tmp = (RouteEndpointAllListener<L, T>)endpoint;
                if (methId != -1) {
                    if (tmp.method instanceof RouteMethodPerMethod) {
                        RouteMethodPerMethod tmp2 = (RouteMethodPerMethod)tmp.method;
                        if (tmp2.obj[methId] == value) {
                            ((RouteMethodPerMethod)tmp2).obj[methId] = null;
                            if (--tmp2.count == 0) {
                                this.deleteEndpoint(endpointNode, dir);
                            }
                            return true;
                        }
                        return false;
                    }
                    return false;
                }
                if (tmp.method instanceof RouteMethodAllMethods) {
                    RouteMethodAllMethods tmp2 = (RouteMethodAllMethods)tmp.method;
                    if (tmp2.obj == value) {
                        this.deleteEndpoint(endpointNode, dir);
                        return true;
                    }
                    return false;
                }
                return false;
            }
            return false;
        }
        if (endpoint instanceof RouteEndpointPerListener) {
            RouteEndpointPerListener<L, T> tmp = (RouteEndpointPerListener<L, T>)endpoint;
            IRouteMethods method = tmp.entries.get(listener);
            if (method != null) {
                if (methId != -1) {
                    if (method instanceof RouteMethodPerMethod) {
                        RouteMethodPerMethod tmp2 = (RouteMethodPerMethod)method;
                        if (tmp2.obj[methId] == value) {
                            ((RouteMethodPerMethod)tmp2).obj[methId] = null;
                            if (--tmp2.count == 0) {
                                tmp.entries.remove(listener);
                                if (tmp.entries.isEmpty()) {
                                    this.deleteEndpoint(endpointNode, dir);
                                }
                            }
                            return true;
                        }
                        return false;
                    }
                    return false;
                }
                if (method instanceof RouteMethodAllMethods) {
                    RouteMethodAllMethods tmp2 = (RouteMethodAllMethods)method;
                    if (tmp2.obj == value) {
                        tmp.entries.remove(listener);
                        if (tmp.entries.isEmpty()) {
                            this.deleteEndpoint(endpointNode, dir);
                        }
                        return true;
                    }
                    return false;
                }
                return false;
            }
            return false;
        }
        return false;
    }

    private void deleteEndpoint(RouteTreeNode<L, T> endpointNode, boolean dir) {
        endpointNode.setEndpoint(dir, null);
        this.deleteNode(endpointNode);
    }

    private void deleteNode(RouteTreeNode<L, T> endpointNode) {
        RouteTreeNode parent = endpointNode.parent;
        if (parent != null && endpointNode.endpoint == null && endpointNode.endpointDir == null && endpointNode.children == null && endpointNode.defaultChild == null) {
            if (parent.defaultChild == endpointNode) {
                parent.defaultChild = null;
            } else if (parent.children != null) {
                Iterator itr = parent.children.values().iterator();
                while (itr.hasNext()) {
                    if (itr.next() != endpointNode) continue;
                    itr.remove();
                    break;
                }
                if (parent.children.isEmpty()) {
                    parent.children = null;
                }
            }
            this.deleteNode(parent);
        }
    }

    public void get(Iterator<CharSequence> tokens, boolean dir, L listener, int methId, Result<T> result) {
        boolean isDir;
        IRouteEndpoint endpoint;
        RouteTreeNode<L, T> endpointNode = this.rootNode.find(tokens, dir);
        if (endpointNode == null) {
            result.result = null;
            return;
        }
        if (dir) {
            endpoint = endpointNode.endpointDir;
            if (endpoint == null) {
                endpoint = endpointNode.endpoint;
                isDir = false;
            } else {
                isDir = true;
            }
        } else {
            endpoint = endpointNode.endpoint;
            if (endpoint == null) {
                endpoint = endpointNode.endpointDir;
                isDir = true;
            } else {
                isDir = false;
            }
        }
        if (endpoint == null) {
            result.result = null;
            return;
        }
        IRouteMethods methods = endpoint.getForListener(listener);
        if (methods == null) {
            result.result = null;
            return;
        }
        T ret = (T)methods.getForMethod(methId);
        if (ret != null) {
            result.result = ret;
            result.directory = isDir;
        } else {
            result.result = null;
        }
    }

    public void getOptions(Iterator<CharSequence> tokens, boolean dir, L listener, Result<List<EnumRequestMethod>> result) {
        boolean isDir;
        IRouteEndpoint endpoint;
        RouteTreeNode<L, T> endpointNode = this.rootNode.find(tokens, dir);
        if (endpointNode == null) {
            result.result = null;
            return;
        }
        if (dir) {
            endpoint = endpointNode.endpointDir;
            if (endpoint == null) {
                endpoint = endpointNode.endpoint;
                isDir = false;
            } else {
                isDir = true;
            }
        } else {
            endpoint = endpointNode.endpoint;
            if (endpoint == null) {
                endpoint = endpointNode.endpointDir;
                isDir = true;
            } else {
                isDir = false;
            }
        }
        if (endpoint == null) {
            result.result = null;
            return;
        }
        IRouteMethods methods = endpoint.getForListener(listener);
        if (methods == null) {
            result.result = null;
            return;
        }
        result.directory = isDir;
        if (methods instanceof RouteMethodPerMethod) {
            if (dir == isDir) {
                RouteMethodPerMethod perMethod = (RouteMethodPerMethod)methods;
                ArrayList<EnumRequestMethod> meths = new ArrayList<EnumRequestMethod>(6);
                Object[] objArr = perMethod.obj;
                int j = perMethod.count;
                for (int i = 0; i < 6 && j > 0; ++i) {
                    if (objArr[i] == null) continue;
                    meths.add(EnumRequestMethod.fromId(i));
                    --j;
                }
                result.result = meths;
            } else {
                result.result = Collections.emptyList();
            }
        } else {
            result.result = allMethods;
        }
    }

    public void dump(Consumer<String> printer) {
        this.dumpNode(this.rootNode, "", printer);
    }

    private void dumpNode(RouteTreeNode<L, T> node, String indent, Consumer<String> printer) {
        printer.accept(indent + "endpoint: " + node.endpoint);
        printer.accept(indent + "endpointDir: " + node.endpointDir);
        printer.accept(indent + "parent: " + node.parent);
        printer.accept(indent + "isDefaultChild: " + node.isDefaultChild);
        printer.accept(indent + "defaultChild:");
        if (node.defaultChild != null) {
            this.dumpNode(node.defaultChild, indent + "  ", printer);
        } else {
            printer.accept(indent + "  (none)");
        }
        printer.accept(indent + "children:");
        if (node.children != null) {
            for (Map.Entry<String, RouteTreeNode<L, T>> etr : node.children.entrySet()) {
                printer.accept(indent + "  \"" + etr.getKey() + "\":");
                this.dumpNode(etr.getValue(), indent + "    ", printer);
            }
        } else {
            printer.accept(indent + "  (none)");
        }
    }

    private static class RouteTreeNode<L, T> {
        protected final RouteTreeNode<L, T> parent;
        protected Map<String, RouteTreeNode<L, T>> children;
        protected RouteTreeNode<L, T> defaultChild;
        protected boolean isDefaultChild;
        protected IRouteEndpoint<L, T> endpoint;
        protected IRouteEndpoint<L, T> endpointDir;

        protected RouteTreeNode(RouteTreeNode<L, T> parent) {
            this.parent = parent;
        }

        protected RouteTreeNode<L, T> find(Iterator<CharSequence> tokens, boolean dir) {
            if (tokens.hasNext()) {
                RouteTreeNode<L, T> r;
                CharSequence n = tokens.next();
                if (this.children != null && (r = this.children.get(n)) != null) {
                    return r.find(tokens, dir);
                }
                if (this.defaultChild != null && (r = this.defaultChild.find(tokens, dir)) != null) {
                    return r;
                }
                if (this.isDefaultChild) {
                    return this;
                }
                return null;
            }
            return this;
        }

        protected RouteTreeNode<L, T> getOrCreateChild(String name) {
            RouteTreeNode<L, T> r;
            if ("*".equals(name)) {
                r = this.defaultChild;
                if (r == null) {
                    this.defaultChild = r = new RouteTreeNode<L, T>(this);
                    r.isDefaultChild = true;
                }
            } else {
                if (this.children == null) {
                    r = null;
                    this.children = new HashMap<String, RouteTreeNode<L, T>>();
                } else {
                    r = this.children.get(name);
                }
                if (r == null) {
                    r = new RouteTreeNode<L, T>(this);
                    this.children.put(name, r);
                }
            }
            return r;
        }

        protected final IRouteEndpoint<L, T> getEndpoint(boolean dir) {
            return dir ? this.endpointDir : this.endpoint;
        }

        protected final void setEndpoint(boolean dir, IRouteEndpoint<L, T> val) {
            if (dir) {
                this.endpointDir = val;
            } else {
                this.endpoint = val;
            }
        }
    }

    private static abstract class IRouteEndpoint<L, T> {
        private IRouteEndpoint() {
        }

        protected abstract IRouteMethods<T> getForListener(L var1);

        protected abstract boolean allListener();
    }

    private static class RouteEndpointAllListener<L, T>
    extends IRouteEndpoint<L, T> {
        protected final IRouteMethods<T> method;

        protected RouteEndpointAllListener(IRouteMethods<T> method) {
            this.method = method;
        }

        @Override
        public IRouteMethods<T> getForListener(L ls) {
            return this.method;
        }

        @Override
        protected boolean allListener() {
            return true;
        }
    }

    private static abstract class IRouteMethods<T> {
        private IRouteMethods() {
        }

        protected abstract T getForMethod(int var1);

        protected abstract boolean allMethod();
    }

    private static class RouteMethodPerMethod<T>
    extends IRouteMethods<T> {
        private final T[] obj = (T[])new Object[6];
        private int count;

        protected RouteMethodPerMethod() {
        }

        @Override
        public T getForMethod(int methId) {
            return this.obj[methId];
        }

        @Override
        protected boolean allMethod() {
            return false;
        }
    }

    private static class RouteEndpointPerListener<L, T>
    extends IRouteEndpoint<L, T> {
        protected final Map<L, IRouteMethods<T>> entries = new HashMap<L, IRouteMethods<T>>(4);

        protected RouteEndpointPerListener() {
        }

        @Override
        public IRouteMethods<T> getForListener(L ls) {
            return this.entries.get(ls);
        }

        @Override
        protected boolean allListener() {
            return false;
        }
    }

    private static class RouteMethodAllMethods<T>
    extends IRouteMethods<T> {
        private final T obj;

        protected RouteMethodAllMethods(T obj) {
            this.obj = obj;
        }

        @Override
        public T getForMethod(int methBit) {
            return this.obj;
        }

        @Override
        protected boolean allMethod() {
            return true;
        }
    }

    public static class Result<T> {
        public T result;
        public boolean directory;
    }
}

