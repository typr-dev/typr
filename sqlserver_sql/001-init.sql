-- SQL Server initialization script
-- This script runs when the container starts

-- Wait for SQL Server to be ready
WAITFOR DELAY '00:00:05';
GO

-- Create the typo database
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'typo')
BEGIN
    CREATE DATABASE typo;
END
GO

USE typo;
GO

-- Create a simple test table to verify connectivity
CREATE TABLE test_connection (
    id INT PRIMARY KEY IDENTITY(1,1),
    message NVARCHAR(100) NOT NULL,
    created_at DATETIME2 DEFAULT GETDATE()
);
GO

INSERT INTO test_connection (message) VALUES ('SQL Server initialized successfully');
GO

PRINT 'SQL Server typo database initialized!';
GO
