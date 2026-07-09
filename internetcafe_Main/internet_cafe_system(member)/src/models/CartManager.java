package models;

import java.util.ArrayList;
import java.util.List;

public class CartManager {

    // Global Cart List
    public static List<CartItem> cartList = new ArrayList<>();

    // ➕ Add Item
    public static void addItem(CartItem item) {
         for (CartItem i : cartList) {

        // 👉 same item စစ်
        if (i.getName().equals(item.getName())) {

            int max = Math.max(i.getStockQty(), item.getStockQty());
            i.setStockQty(max);
            int combined = i.getQuantity() + item.getQuantity();
            if (combined > max) {
                combined = max;
            }
            i.setQuantity(combined);

            return; // 🔥 stop (new item မထည့်တော့)
        }
    }

    // 👉 မရှိသေးရင်သာ add
    cartList.add(item);
    }

    // ❌ Remove Item
    public static void removeItem(CartItem item) {
        cartList.remove(item);
    }

    // 🔄 Clear Cart
    public static void clearCart() {
        cartList.clear();
    }

    // 📦 Get All Items
    public static List<CartItem> getCartList() {
        return cartList;
    }
    public static void removeLastItem() {

    if (!cartList.isEmpty()) {
        cartList.clear(); // 🔥 last item remove
    }
}
}