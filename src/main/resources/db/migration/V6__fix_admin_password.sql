-- V6: Fix admin password hash (Admin@123, BCrypt cost 12)
UPDATE users
SET password_hash = '$2a$12$o.X.pno8YREvJu8YeGUjie7ExvmQ14ECPlr.nfvEs4T6b92lT2pgy'
WHERE username = 'admin';