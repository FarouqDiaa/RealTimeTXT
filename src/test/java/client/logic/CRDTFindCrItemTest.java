package client.logic;

import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

import com.realtimetxt.client.logic.CRDT;
import com.realtimetxt.client.logic.CRDTItem;

public class CRDTFindCrItemTest {
    @Test
    public void testFindCrItemFirstLevelChild() {
        // Create a root item
        CRDTItem root = new CRDTItem(null, "root");
        CRDT crdt = new CRDT(root);

        // Create child items
        CRDTItem child1 = new CRDTItem(root, "child1");
        CRDTItem child2 = new CRDTItem(root, "child2");

        // Add children to root
        root.addChild(child1);
        root.addChild(child2);

        // Test finding the root item
        Assert.assertEquals(root, crdt.findCrItem(root.getId()));

        // Test finding the child items
        Assert.assertEquals(child1, crdt.findCrItem(child1.getId()));
        Assert.assertEquals(child2, crdt.findCrItem(child2.getId()));

        // Test finding a non-existent item
        Assert.assertNull(crdt.findCrItem(UUID.randomUUID()));
    }

    @Test
    public void testFindCrItemSecondLevelChild() {
        // Create a root item
        CRDTItem root = new CRDTItem(null, "root");
        CRDT crdt = new CRDT(root);

        // Create child items
        CRDTItem child1 = new CRDTItem(root, "child1");
        CRDTItem child2 = new CRDTItem(root, "child2");

        // Add children to root
        root.addChild(child1);
        root.addChild(child2);

        // Create second level child items
        CRDTItem grandChild1 = new CRDTItem(child1, "grandChild1");
        CRDTItem grandChild2 = new CRDTItem(child2, "grandChild2");

        // Add second level children to their respective parents
        child1.addChild(grandChild1);
        child2.addChild(grandChild2);

        // Test finding the second level child items
        Assert.assertEquals(grandChild1, crdt.findCrItem(grandChild1.getId()));
        Assert.assertEquals(grandChild2, crdt.findCrItem(grandChild2.getId()));

        // Test finding a non-existent item
        Assert.assertNull(crdt.findCrItem(UUID.randomUUID()));

        CRDTItem greatGrandChild1 = new CRDTItem(grandChild1, "greatGrandChild1");
        grandChild1.addChild(greatGrandChild1);
        Assert.assertEquals(greatGrandChild1, crdt.findCrItem(greatGrandChild1.getId()));
    }

    @Test
    public void testFindCrItemMultipleLevels() {
        // Create a root item
        CRDTItem root = new CRDTItem(null, "root");
        CRDT crdt = new CRDT(root);

        // Create child items
        CRDTItem child1 = new CRDTItem(root, "child1");
        CRDTItem child2 = new CRDTItem(root, "child2");

        // Add children to root
        root.addChild(child1);
        root.addChild(child2);

        // Create second level child items
        CRDTItem grandChild1 = new CRDTItem(child1, "grandChild1");
        CRDTItem grandChild2 = new CRDTItem(child2, "grandChild2");

        // Add second level children to their respective parents
        child1.addChild(grandChild1);
        child2.addChild(grandChild2);

        // Create third level child items
        CRDTItem greatGrandChild1 = new CRDTItem(grandChild1, "greatGrandChild1");
        CRDTItem greatGrandChild2 = new CRDTItem(grandChild2, "greatGrandChild2");

        // Add third level children to their respective parents
        grandChild1.addChild(greatGrandChild1);
        grandChild2.addChild(greatGrandChild2);

        // Test finding the third level child items
        Assert.assertEquals(greatGrandChild1, crdt.findCrItem(greatGrandChild1.getId()));
        Assert.assertEquals(greatGrandChild2, crdt.findCrItem(greatGrandChild2.getId()));

        // Test finding a non-existent item
        Assert.assertNull(crdt.findCrItem(UUID.randomUUID()));
    }

    @Test
    public void testFindCrItemNonExistent() {
        // Create a root item
        CRDTItem root = new CRDTItem(null, "root");
        CRDT crdt = new CRDT(root);

        // Test finding a non-existent item
        Assert.assertNull(crdt.findCrItem(UUID.randomUUID()));
    }

    @Test
    public void testFindCrItemEmptyTree() {
        // Create an empty CRDT
        CRDT crdt = new CRDT((CRDTItem) null);

        // Test finding a non-existent item in an empty tree
        Assert.assertNull(crdt.findCrItem(UUID.randomUUID()));
    }

    @Test
    public void testFindCRDTUnBalancedTree() {
        // Create a root item
        CRDTItem root = new CRDTItem(null, "root");
        CRDT crdt = new CRDT(root);

        // Create child items
        CRDTItem child1 = new CRDTItem(root, "child1");
        root.addChild(child1);

        CRDTItem child2 = new CRDTItem(child1, "child2");
        child1.addChild(child2);

        CRDTItem child3 = new CRDTItem(child2, "child3");
        child2.addChild(child3);

        // Test finding the child items
        Assert.assertEquals(child3, crdt.findCrItem(child3.getId()));
        Assert.assertEquals(child2, crdt.findCrItem(child2.getId()));
        Assert.assertEquals(child1, crdt.findCrItem(child1.getId()));
        Assert.assertEquals(root, crdt.findCrItem(root.getId()));

        // Test finding a non-existent item
        Assert.assertNull(crdt.findCrItem(UUID.randomUUID()));
    }
}
