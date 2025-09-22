📦 Product Management Application

A Java Swing desktop application for managing products, shipments, sales, and clients.
The system is built with SQLite (via JDBC) to provide a lightweight and portable database solution.

🚀 Features

Product Management

Add, edit, delete, and view products.

Track product quantities and prices.

Client Management

Add, edit, and remove clients.

Track purchases and preferences.

Sales Tracking

Record new sales and calculate totals.

Manage sales data per client.

Shipments

Add shipment details including:

Product quantity

Product price

Prime cost

Delivery costs

Employee and equipment expenses

Automatically calculates gross profit and revenue per shipment.

Database

Uses SQLite with sqlite-jdbc for portability (single file database).

Ensures data consistency with transactions and commits.

GUI

Implemented with Java Swing (JFrame, JDialog, JTable, JButton, etc.).

Multiple management windows:

MainAppWindow

ProductWindow

ClientWindow

SalesWindow

ShipmentWindow

🛠️ Technologies Used

Java (Swing)

SQLite with sqlite-jdbc

Maven / Gradle (optional, depending on your setup)

⚙️ Installation & Usage

Clone the repository:

git clone https://github.com/your-username/product-management-app.git
cd product-management-app


Open the project in IntelliJ IDEA, Eclipse, or any IDE with Java support.

Ensure you have Java 17+ installed:

java -version


Run the application:

Compile and execute MainAppWindow.java

The app will automatically create an SQLite database file (app.db) if it doesn’t exist.

📊 Database Structure

The app works with the following main tables:

Products

Clients

Sales

Shipments

(Relationships managed inside DatabaseManager.java)

👨‍💻 Author

Developed by Maksym Redchenko
📧 Contact: the.maxim.red@gmail.com
