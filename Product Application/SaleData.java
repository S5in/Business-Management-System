public class SaleData {
    private final int quantity;
    private final String saleDate;
    private final String sellerName;
    private final String productName;

    public SaleData(int quantity, String saleDate, String sellerName, String productName) {
        this.quantity = quantity;
        this.saleDate = saleDate;
        this.sellerName = sellerName;
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getSaleDate() {
        return saleDate;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getProductName() {
        return productName;
    }
}