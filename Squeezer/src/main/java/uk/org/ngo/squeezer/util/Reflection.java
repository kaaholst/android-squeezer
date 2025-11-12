/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Reflection utility methods
 *
 * @author kaa
 */
public class Reflection {

    /**
     * <p>Return the actual type parameter of the supplied class for the type variable at the
     * supplied position in the supplied base class or interface. <p>The method returns null if the
     * class can't be resolved. See {@link #genericTypeResolver(Class, Class)} for details on what
     * can't be resolved, and how to work around it.
     *
     * @param currentClass The current class which must extend or implement <code>base</code>
     * @param base Generic base class or interface which type variable we wish to resolve.
     * @param genericArgumentNumber The position of the type variable we are interested in.
     *
     * @return The actual type parameter at the supplied position in <code>base</code> as a class or
     * null.
     *
     * @see #genericTypeResolver(Class, Class)
     */
    public static Class<?> getGenericClass(Class<?> currentClass,
            Class<?> base, int genericArgumentNumber) {
        Type[] genericTypes = genericTypeResolver(currentClass, base);
        Type type = genericArgumentNumber < genericTypes.length
                ? genericTypes[genericArgumentNumber] : null;

        if (type instanceof Class<?> aClass) return aClass;
        return null;
    }

    /**
     * <p>Resolve actual type arguments of the supplied class for the type variables in the supplied
     * generic base class or interface. <p>If the types can't be resolved an empty array is
     * returned.<br> <p><b>NOTE</b><br>This will only resolve generic parameters when they are
     * declared in the class, it wont resolve instances of generic types. So for example:
     * <pre>
     * <code>new LinkedList<String>()</code> cant'be resolved but
     * <code>new LinkedList<String>(){}</code> can. (Notice the subclassing of the generic
     * collection)
     * </pre>
     *
     * @param currentClass The current class which must extend or implement <code>base</code>
     * @param base Generic base class or interface which type variables we wish to resolve.
     *
     * @return Actual type arguments for <code>base</code> used in <code>currentClass</code>.
     *
     * @see #getGenericClass(Class, Class, int)
     */
    public static Type[] genericTypeResolver(Class<?> currentClass,
            Class<?> base) {
        Type[] actualTypeArguments = null;

        while (currentClass != Object.class) {
            if (currentClass.isAssignableFrom(base)) {
                return (actualTypeArguments == null ? currentClass.getTypeParameters()
                        : actualTypeArguments);
            }

            if (base.isInterface()) {
                Type[] actualTypes = genericInterfaceResolver(currentClass, base,
                        actualTypeArguments);
                if (actualTypes != null) {
                    return actualTypes;
                }
            }

            actualTypeArguments = mapTypeArguments(currentClass,
                    currentClass.getGenericSuperclass(), actualTypeArguments);
            currentClass = currentClass.getSuperclass();
        }

        return new Type[0];
    }


    /**
     * Resolve actual type arguments of the supplied class for the type variables in the supplied
     * generic interface.
     *
     * @param currentClass The current class which may implement <code>base</code>
     * @param baseInterface Generic interface which type variables we wish to resolve.
     * @param actualTypeArguments Resolved type arguments from parent
     *
     * @return Actual type arguments for <code>baseInterface</code> used in
     * <code>currentClass</code> or null.
     *
     * @see #getGenericClass(Class, Class, int)
     */
    private static Type[] genericInterfaceResolver(Class<?> currentClass,
            Class<?> baseInterface, Type[] pActualTypeArguments) {
        Class<?>[] interfaces = currentClass.getInterfaces();
        Type[] genericInterfaces = currentClass.getGenericInterfaces();
        for (int ifno = 0; ifno < genericInterfaces.length; ifno++) {
            Type[] actualTypeArguments = mapTypeArguments(currentClass, genericInterfaces[ifno],
                    pActualTypeArguments);

            if (genericInterfaces[ifno] instanceof ParameterizedType parameterizedType) {
                if (baseInterface.equals(parameterizedType.getRawType())) {
                    return actualTypeArguments;
                }
            }

            Type[] resolvedTypes = genericInterfaceResolver(interfaces[ifno], baseInterface,
                    actualTypeArguments);
            if (resolvedTypes != null) {
                return resolvedTypes;
            }
        }

        return null;
    }

    /**
     * Map the resolved type arguments of the given class to the type parameters of the supplied
     * superclass or direct interface.
     *
     * @param currentClass The class with the supplied resolved type arguments
     * @param type Superclass or direct interface of <code>currentClass</code>
     * @param actualTypeArguments Resolved type arguments of of <code>currentClass</code>
     *
     * @return The resolved type arguments mapped to the given superclass or direct interface
     */
    private static Type[] mapTypeArguments(Class<?> currentClass, Type type,
            Type[] actualTypeArguments) {
        if (type instanceof ParameterizedType pType) {
            if (actualTypeArguments == null) {
                return pType.getActualTypeArguments();
            }

            TypeVariable<?>[] typeParameters = currentClass.getTypeParameters();
            Type[] actualTypes = pType.getActualTypeArguments();
            Type[] newActualTypeArguments = new Type[actualTypes.length];
            for (int i = 0; i < actualTypes.length; i++) {
                newActualTypeArguments[i] = actualTypes[i];
                for (int j = 0; j < typeParameters.length; j++) {
                    if (actualTypes[i].equals(typeParameters[j])) {
                        newActualTypeArguments[i] = actualTypeArguments[j];
                        break;
                    }
                }
            }
            return newActualTypeArguments;

        } else {
            return null;
        }
    }

}
