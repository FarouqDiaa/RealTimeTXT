package client.logic;

import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import com.realtimetxt.client.logic.CRDT;
import com.realtimetxt.client.logic.CRDTItem;
import com.realtimetxt.shared.CRDTOperation;
import com.realtimetxt.shared.enums.OperationType;

public class CRDTNewOperationTest {

    @Test
    public void testNewOperationInsert() {
        // Create a root item
        CRDTItem root = new CRDTItem(null, "root");
        CRDT crdt = new CRDT(root);

        // Create a new operation to insert a child item
        UUID parentId = root.getId();
        String value = "child1";
        UUID itemId = UUID.randomUUID(); // This is not used in the test, but can be set if needed

        CRDTOperation newOperation = new CRDTOperation(
                "1",
                OperationType.INSERT,
                value,
                parentId,
                itemId);

        // Perform the operation
        crdt.newOperation(newOperation);

        // Verify that the child item was added to the root
        Assert.assertEquals(1, root.getChildren().size());
        Assert.assertEquals(itemId, root.getChildren().get(0).getId());
        Assert.assertEquals("child1", root.getChildren().get(0).getValue());
        Assert.assertEquals(itemId, crdt.findCrItem(itemId).getId());
    }

    @Test
    public void testNewOperationMultipleInsertions() {
        // Create a root item
        CRDTItem root = new CRDTItem(null, "root");
        CRDT crdt = new CRDT(root);

        // Create multiple operations to insert child items
        UUID parentId = root.getId();
        String value1 = "child1";
        String value2 = "child2";
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();

        CRDTOperation newOperation1 = new CRDTOperation(
                "1",
                OperationType.INSERT,
                value1,
                parentId,
                itemId1);
        CRDTOperation newOperation2 = new CRDTOperation(
                "1",
                OperationType.INSERT,
                value2,
                parentId,
                itemId2);

        // Perform the operations
        crdt.newOperation(newOperation1);
        crdt.newOperation(newOperation2);

        // Verify that both child items were added to the root
        Assert.assertEquals(2, root.getChildren().size());
        Assert.assertEquals(itemId2, root.getChildren().get(0).getId());
        Assert.assertEquals("child2", root.getChildren().get(0).getValue());
        Assert.assertEquals(itemId1, root.getChildren().get(1).getId());
        Assert.assertEquals("child1", root.getChildren().get(1).getValue());
        Assert.assertEquals(2, crdt.findCrItem(parentId).getChildren().size());
    }

    @Test
    public void testNewOperationOneSequencialInsert() {
        // Create a root item
        CRDTItem root = new CRDTItem(null, "root");
        CRDT crdt = new CRDT(root);

        // Create a new operation to insert a child item
        UUID parentId = root.getId();
        String value1 = "child1";
        UUID itemId1 = UUID.randomUUID(); // This is not used in the test, but can be set if needed

        CRDTOperation newOperation = new CRDTOperation(
                "1",
                OperationType.INSERT,
                value1,
                parentId,
                itemId1);
        crdt.newOperation(newOperation);

        String value2 = "child2";
        UUID itemId2 = UUID.randomUUID(); // This is not used in the test, but can be set if needed
        CRDTOperation newOperation2 = new CRDTOperation(
                "1",
                OperationType.INSERT,
                value2,
                newOperation.getItemId(),
                itemId2);

        crdt.newOperation(newOperation2);

        // Verify that the child item was added to the root
        Assert.assertEquals(1, root.getChildren().size());
        Assert.assertEquals(itemId1, root.getChildren().get(0).getId());
        Assert.assertEquals("child1", root.getChildren().get(0).getValue());
        Assert.assertEquals(1, root.getChildren().get(0).getChildren().size());
        Assert.assertEquals(itemId2, root.getChildren().get(0).getChildren().get(0).getId());
        Assert.assertEquals("child2", root.getChildren().get(0).getChildren().get(0).getValue());
    }
}
