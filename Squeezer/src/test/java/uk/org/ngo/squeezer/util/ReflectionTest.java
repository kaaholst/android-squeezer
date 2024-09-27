package uk.org.ngo.squeezer.util;

import junit.framework.TestCase;

import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReflectionTest extends TestCase {

    class Item {}
    class Item1 extends Item {}
    class Item2 extends Item {}

    class GroupItem extends Item {}
    class GroupItem1 extends GroupItem {}
    class GroupItem2 extends GroupItem {}


    class A<T extends Item> {}
    class B1 extends A<Item1> {}
    class B2 extends A<Item2> {}
    class C<T extends GroupItem> extends A<T> {}
    class D1 extends C<GroupItem1> {}
    class D2 extends C<GroupItem2> {}

    class AA<T1 extends Item, T2 extends Item> {}
    class BB extends AA<Item1, Item2> {}

    interface I<T extends Item> {}
    class AI<T extends Item> implements I<T> {}
    class BI1 extends AI<Item1> {}
    class BI2 extends AI<Item2> {}
    class BIG1 extends AI<GroupItem1> {}
    class BIG2 extends AI<GroupItem2> {}
    class CIG<T extends GroupItem> extends AI<T> {}
    class CIG1 extends CIG<GroupItem1> {}
    class CIG2 extends CIG<GroupItem2> {}

    class StrangeExtend<T extends Item> extends A<Item1> {}

    // Resolves to Item2 for StrangeExtend and Item1 for A
    class Item1ToItem2 extends StrangeExtend<Item2> {}

    interface _I<T extends Item> {}
    interface II<T1 extends Item, T2 extends Item> extends _I<T1> {}
    class AII<T1 extends Item, T2 extends Item> implements II<T1, T2> {}
    class BII implements II<Item1, Item2> {}
    class CII extends BII {}
    class BAII extends AII<Item1, Item2> {}
    class BIII implements II<Item1, Item2>, I<Item2> {}
    class AIII<T1 extends Item, T2 extends Item> implements II<T1, T2>, I<T2> {}

    interface __I<T extends Item> {}
    class BAIII extends AIII<Item1, GroupItem1> implements __I<GroupItem2> {}

    class SwapOrder1<T1 extends Item, T2 extends Item> extends AA<T2, T1> {}
    class SwapOrder2<T1 extends Item, T2 extends Item> implements II<T2, T1> {}
    class SwapOrder3<T1 extends Item, T2 extends Item> extends AII<T2, T1> {}

    class Activity { }
    class SwitchTypeI<A extends Activity> implements I<Item1> {}
    class SwitchTypeAI<A extends Activity> extends AI<Item1> {}

    interface FunctionInterface<T> {
        int get();
    }

    public void testGenericTypeResolver() {
        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(new B1().getClass(), A.class));
        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(B1.class, A.class));
        assertTypesEquals(new Type[]{Item2.class}, Reflection.genericTypeResolver(new B2().getClass(), A.class));
        assertTypesEquals(new Type[]{Item2.class}, Reflection.genericTypeResolver(B2.class, A.class));

        assertTypesEquals(new Type[]{GroupItem1.class}, Reflection.genericTypeResolver(D1.class, A.class));
        assertTypesEquals(new Type[]{GroupItem2.class}, Reflection.genericTypeResolver(D2.class, A.class));
        assertTypesEquals(new Type[]{GroupItem1.class}, Reflection.genericTypeResolver(new D1().getClass(), C.class));
        assertTypesEquals(new Type[]{GroupItem2.class}, Reflection.genericTypeResolver(new D2().getClass(), C.class));

        assertTypesEquals(new Type[]{Item1.class, Item2.class}, Reflection.genericTypeResolver(BII.class, II.class));
        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(BII.class, _I.class));
        assertTypesEquals(new Type[]{Item1.class, Item2.class}, Reflection.genericTypeResolver(CII.class, II.class));
        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(CII.class, _I.class));
        assertTypesEquals(new Type[]{Item1.class, Item2.class}, Reflection.genericTypeResolver(BAII.class, II.class));
        assertTypesEquals(new Type[]{Item1.class, Item2.class}, Reflection.genericTypeResolver(BAII.class, AII.class));
        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(BAII.class, _I.class));
        assertTypesEquals(new Type[]{Item1.class, Item2.class}, Reflection.genericTypeResolver(BIII.class, II.class));
        assertTypesEquals(new Type[]{Item2.class}, Reflection.genericTypeResolver(BIII.class, I.class));
        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(BIII.class, _I.class));
        assertTypesEquals(new Type[]{Item1.class, GroupItem1.class}, Reflection.genericTypeResolver(BAIII.class, II.class));
        assertTypesEquals(new Type[]{Item1.class, GroupItem1.class}, Reflection.genericTypeResolver(BAIII.class, AIII.class));
        assertTypesEquals(new Type[]{GroupItem1.class}, Reflection.genericTypeResolver(BAIII.class, I.class));
        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(BAIII.class, _I.class));
        assertTypesEquals(new Type[]{GroupItem2.class}, Reflection.genericTypeResolver(BAIII.class, __I.class));

        assertTypesEquals(new Type[]{Item2.class, Item1.class}, Reflection.genericTypeResolver(new SwapOrder1<Item1, Item2>() {}.getClass(), AA.class));
        assertTypesEquals(new Type[]{Item2.class, Item1.class}, Reflection.genericTypeResolver(new SwapOrder2<Item1, Item2>() {}.getClass(), II.class));
        assertTypesEquals(new Type[]{Item2.class, Item1.class}, Reflection.genericTypeResolver(new SwapOrder3<Item1, Item2>() {}.getClass(), II.class));
        assertTypesEquals(new Type[]{Item2.class, Item1.class}, Reflection.genericTypeResolver(new SwapOrder3<Item1, Item2>() {}.getClass(), AII.class));

        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(new SwitchTypeI<Activity>() {}.getClass(), I.class));
        assertTypesEquals(new Type[]{Item1.class}, Reflection.genericTypeResolver(new SwitchTypeAI<Activity>() {}.getClass(), I.class));
    }

    private void assertTypesEquals(Type[] expected, Type[] actual) {
        assertEquals(typeArray(expected), typeArray(actual));
    }

    private String typeArray(Type[] types) {
        StringBuilder sb = new StringBuilder();
        for (Type type : types) {
            sb.append(sb.length() > 0 ? "," : "[");
            sb.append(type);
        }
        return sb.append("]").toString();
    }

    public void testGetGenericClass() {
        assertEquals(Item1.class, Reflection.getGenericClass(new B1().getClass(), A.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(B1.class, A.class, 0));
        assertEquals(Item2.class, Reflection.getGenericClass(new B2().getClass(), A.class, 0));
        assertEquals(Item2.class, Reflection.getGenericClass(B2.class, A.class, 0));

        assertEquals(GroupItem1.class, Reflection.getGenericClass(new D1().getClass(), A.class, 0));
        assertEquals(GroupItem1.class, Reflection.getGenericClass(D1.class, A.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(new D2().getClass(), A.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(D2.class, A.class, 0));

        assertEquals(GroupItem1.class, Reflection.getGenericClass(new D1().getClass(), C.class, 0));
        assertEquals(GroupItem1.class, Reflection.getGenericClass(D1.class, C.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(new D2().getClass(), C.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(D2.class, C.class, 0));

        assertEquals(Item1.class, Reflection.getGenericClass(BB.class, AA.class, 0));
        assertEquals(Item2.class, Reflection.getGenericClass(BB.class, AA.class, 1));

        assertEquals(Item1.class, Reflection.getGenericClass(BI1.class, I.class, 0));
        assertEquals(Item2.class, Reflection.getGenericClass(BI2.class, I.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(BI1.class, AI.class, 0));
        assertEquals(Item2.class, Reflection.getGenericClass(BI2.class, AI.class, 0));
        assertEquals(GroupItem1.class, Reflection.getGenericClass(BIG1.class, I.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(BIG2.class, I.class, 0));
        assertEquals(GroupItem1.class, Reflection.getGenericClass(BIG1.class, AI.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(BIG2.class, AI.class, 0));
        assertEquals(GroupItem1.class, Reflection.getGenericClass(CIG1.class, I.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(CIG2.class, I.class, 0));
        assertEquals(GroupItem1.class, Reflection.getGenericClass(CIG1.class, AI.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(CIG2.class, AI.class, 0));
        assertEquals(GroupItem1.class, Reflection.getGenericClass(CIG1.class, CIG.class, 0));
        assertEquals(GroupItem2.class, Reflection.getGenericClass(CIG2.class, CIG.class, 0));

        assertEquals(Item1.class, Reflection.getGenericClass(Item1ToItem2.class, A.class, 0));
        assertEquals(Item2.class, Reflection.getGenericClass(Item1ToItem2.class, StrangeExtend.class, 0));

        assertEquals(Item1.class, Reflection.getGenericClass(BB.class, AA.class, 0));
        assertEquals(Item2.class, Reflection.getGenericClass(BB.class, AA.class, 1));

        assertEquals(Item1.class, Reflection.getGenericClass(new SwitchTypeI<Activity>() {}.getClass(), I.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(new SwitchTypeAI<Activity>() {}.getClass(), I.class, 0));

        // Lambda doesn't resolve, but anonymous implementation does
        assertEquals(null, Reflection.getGenericClass(((FunctionInterface<Item>) () -> 0).getClass(), FunctionInterface.class, 0));
        assertEquals(Item.class, Reflection.getGenericClass(new FunctionInterface<Item>(){
            @Override
            public int get() {
                return 0;
            }
        }.getClass(), FunctionInterface.class, 0));
    }

    public void testResolveGenericCollections() {
        List<Item1> itemList = new ArrayList<Item1>() {
            private static final long serialVersionUID = 1L;
        };
        Set<Item1> itemSet = new HashSet<Item1>() {
            private static final long serialVersionUID = 1L;
        };
        Map<String, Item1> itemMap = new HashMap<String, ReflectionTest.Item1>() {
            private static final long serialVersionUID = 1L;
        };
        List<Integer> intList = new ArrayList<Integer>() {
            private static final long serialVersionUID = 1L;
        };
        Set<Integer> intSet = new HashSet<Integer>() {
            private static final long serialVersionUID = 1L;
        };

        assertEquals(Item1.class, Reflection.getGenericClass(itemList.getClass(), Collection.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(itemList.getClass(), List.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(itemList.getClass(), AbstractCollection.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(itemList.getClass(), AbstractList.class, 0));

        assertEquals(Item1.class, Reflection.getGenericClass(itemSet.getClass(), Collection.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(itemSet.getClass(), Set.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(itemSet.getClass(), AbstractCollection.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(itemSet.getClass(), AbstractSet.class, 0));

        assertEquals(String.class, Reflection.getGenericClass(itemMap.getClass(), Map.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(itemMap.getClass(), Map.class, 1));
        assertEquals(String.class, Reflection.getGenericClass(itemMap.getClass(), AbstractMap.class, 0));
        assertEquals(Item1.class, Reflection.getGenericClass(itemMap.getClass(), AbstractMap.class, 1));

        assertEquals(Integer.class, Reflection.getGenericClass(intList.getClass(), Collection.class, 0));
        assertEquals(Integer.class, Reflection.getGenericClass(intList.getClass(), List.class, 0));
        assertEquals(Integer.class, Reflection.getGenericClass(intList.getClass(), AbstractCollection.class, 0));
        assertEquals(Integer.class, Reflection.getGenericClass(intList.getClass(), AbstractList.class, 0));

        assertEquals(Integer.class, Reflection.getGenericClass(intSet.getClass(), Collection.class, 0));
        assertEquals(Integer.class, Reflection.getGenericClass(intSet.getClass(), Set.class, 0));
        assertEquals(Integer.class, Reflection.getGenericClass(intSet.getClass(), AbstractCollection.class, 0));
        assertEquals(Integer.class, Reflection.getGenericClass(intSet.getClass(), AbstractSet.class, 0));

        // Generic variables doesn't resolve, but subclassing the generic interface does
        assertEquals(null, Reflection.getGenericClass(new ArrayList<Integer>().getClass(), List.class, 0));
        assertEquals(Integer.class, Reflection.getGenericClass(new ArrayList<Integer>(){}.getClass(), List.class, 0));
    }

}
