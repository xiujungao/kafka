@echo off
REM Sample script to test the Kafka Orders application on Windows

echo Creating a test order...

curl -X POST http://localhost:8080/api/orders ^
  -H "Content-Type: application/json" ^
  -d "{\"customerId\": \"customer-123\", \"productName\": \"Laptop\", \"quantity\": 1, \"price\": 999.99, \"status\": \"PENDING\"}"

echo.
echo Order created! Check the application logs to see the consumer processing the message.

