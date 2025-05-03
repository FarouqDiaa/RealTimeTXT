package client.logic;

import org.junit.Assert;
import org.junit.Test;

import com.realtimetxt.client.logic.CRDTController;

public class CRDTControllerTest {
    @Test
    public void testTextChanged() {
        // Create a CRDTController instance
        CRDTController crdtController = new CRDTController();

        // Simulate a text change operation
        crdtController.textChanged("H", 0);

        // Check if the text was updated correctly
        String renderedText = crdtController.renderText();
        Assert.assertEquals("H", renderedText);
    }

    @Test
    public void testRenderText() {
        // Create a CRDTController instance
        CRDTController crdtController = new CRDTController();

        // Simulate multiple text change operations
        crdtController.textChanged("H", 0);
        crdtController.textChanged("He", 1);
        crdtController.textChanged("Hel", 2);
        crdtController.textChanged("Hell", 3);
        crdtController.textChanged("Hello", 4);

        // Check if the text was updated correctly
        String renderedText = crdtController.renderText();
        Assert.assertEquals("Hello", renderedText);
    }

    @Test
    public void testRenderWithDifferentOrder() {
        // Create a CRDTController instance
        CRDTController crdtController = new CRDTController();

        // Simulate multiple text change operations in different order
        crdtController.textChanged("H", 0);
        crdtController.textChanged("Hl", 1);
        crdtController.textChanged("Hel", 1);

        // Check if the text was updated correctly
        String renderedText = crdtController.renderText();
        Assert.assertEquals("Hel", renderedText);
    }

    @Test
    public void testRenderWithDifferentOrder2() {
        // Create a CRDTController instance
        CRDTController crdtController = new CRDTController();

        // Simulate multiple text change operations in different order
        crdtController.textChanged("H", 0);
        crdtController.textChanged("Hl", 1);
        crdtController.textChanged("Hel", 1);
        crdtController.textChanged("Hell", 3);

        // Check if the text was updated correctly
        String renderedText = crdtController.renderText();
        Assert.assertEquals("Hell", renderedText);

        crdtController.textChanged("Hello", 4);
        renderedText = crdtController.renderText();
        Assert.assertEquals("Hello", renderedText);
    }

    @Test
    public void testDeleteText() {
        // Create a CRDTController instance
        CRDTController crdtController = new CRDTController();

        // Simulate a text change operation
        crdtController.textChanged("H", 0);
        crdtController.textChanged("He", 1);
        crdtController.textChanged("Hel", 2);
        crdtController.textChanged("Hell", 3);
        crdtController.textChanged("Hello", 4);

        String renderedText = crdtController.renderText();
        Assert.assertEquals("Hello", renderedText);

        // Simulate a delete operation
        crdtController.textChanged("Hell", 5);

        // Check if the text was updated correctly after deletion
        renderedText = crdtController.renderText();
        Assert.assertEquals("Hell", renderedText);
    }

    @Test
    public void testDeleteText2() {
        // Create a CRDTController instance
        CRDTController crdtController = new CRDTController();

        // Simulate a text change operation
        crdtController.textChanged("H", 0);
        crdtController.textChanged("He", 1);
        crdtController.textChanged("Hel", 2);
        crdtController.textChanged("Hell", 3);
        crdtController.textChanged("Hello", 4);

        String renderedText = crdtController.renderText();
        Assert.assertEquals("Hello", renderedText);

        // Simulate a delete operation
        crdtController.textChanged("Hell", 5);
        crdtController.textChanged("Hello", 4);

        // Check if the text was updated correctly after deletion
        renderedText = crdtController.renderText();
        Assert.assertEquals("Hello", renderedText);
    }

    @Test
    public void testDeleteText3() {
        // Create a CRDTController instance
        CRDTController crdtController = new CRDTController();

        // Simulate a text change operation
        crdtController.textChanged("H", 0);
        crdtController.textChanged("He", 1);
        crdtController.textChanged("Hel", 2);
        crdtController.textChanged("Hell", 3);
        crdtController.textChanged("Hello", 4);

        String renderedText = crdtController.renderText();
        Assert.assertEquals("Hello", renderedText);

        // Simulate a delete operation
        crdtController.textChanged("Hell", 5);
        crdtController.textChanged("Hello", 4);
        crdtController.textChanged("Helo", 4);

        // Check if the text was updated correctly after deletion
        renderedText = crdtController.renderText();
        Assert.assertEquals("Helo", renderedText);
    }
}
