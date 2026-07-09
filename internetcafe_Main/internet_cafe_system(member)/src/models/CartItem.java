package models;

public class CartItem {

    private String name;
    private double price;
    private int quantity;
    private String imagePath;
    /** Max units allowed in cart (matches product stock). */
    private int stockQty;

    // Constructor
    public CartItem(String name, double price, int quantity, String imagePath, int stockQty) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.imagePath = imagePath;
        this.stockQty = stockQty;
    }

    // Getters
    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getImagePath() {
        return imagePath;
    }

    public int getStockQty() {
        return stockQty;
    }

    public void setStockQty(int stockQty) {
        this.stockQty = stockQty;
    }

    // Optional: set quantity (update)
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}