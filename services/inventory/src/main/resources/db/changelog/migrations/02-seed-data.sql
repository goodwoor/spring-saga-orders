--liquibase formatted sql

--changeset goodwoor:2
INSERT INTO items (id, cost, amount, name, description) VALUES
    (1,   999.00,  50, 'Wireless Headphones',   'Bluetooth over-ear headphones with noise cancellation'),
    (2,   150.00, 120, 'USB-C Cable',            '2m braided USB-C charging cable'),
    (3,   349.50,  80, 'Mechanical Keyboard',    'RGB mechanical keyboard, brown switches'),
    (4,  2499.00,  15, '4K Monitor',             '27-inch 4K IPS monitor, 144Hz'),
    (5,   899.00,  40, 'Webcam HD',              '1080p webcam with built-in microphone'),
    (6,  1601.99,  25, 'Laptop Stand',           'Aluminum adjustable laptop stand'),
    (7,  1000.00,  30, 'External SSD 1TB',       'Portable NVMe SSD, USB 3.2'),
    (8,   199.00, 200, 'Mouse Pad XL',           'Large desk mouse pad, non-slip base'),
    (9,  1149.00,  35, 'Wireless Mouse',         'Ergonomic wireless mouse, rechargeable'),
    (10,  749.00,  60, 'USB Hub',                '7-port USB 3.0 hub with power adapter'),
    (11,  700.00,  45, 'Desk Lamp',              'LED desk lamp with adjustable brightness'),
    (12,  299.50,  90, 'Phone Case',             'Protective phone case, shock-absorbent'),
    (13, 1899.50,  20, 'Tablet 10"',             '10-inch tablet, 128GB storage'),
    (14, 2799.00,  10, 'Gaming Chair',           'Ergonomic gaming chair with lumbar support'),
    (15, 1099.00,  55, 'Bluetooth Speaker',      'Portable waterproof Bluetooth speaker');

INSERT INTO reservations (id, item_id, order_id, reserved_amount) VALUES
    (1,  1,  1, 1),
    (2,  2,  1, 2),
    (3,  3,  2, 1),
    (4,  4,  3, 1),
    (5,  5,  4, 1),
    (6,  1,  5, 2),
    (7,  6,  5, 1),
    (8,  7,  5, 1),
    (9,  8,  6, 1),
    (10, 2,  7, 1),
    (11, 9,  7, 1),
    (12, 10, 8, 1),
    (13, 4,  9, 1),
    (14, 11, 9, 1),
    (15, 12, 10, 2),
    (16, 13, 11, 1),
    (17, 3,  12, 1),
    (18, 8,  12, 1);

SELECT setval('items_id_seq', (SELECT MAX(id) FROM items));
SELECT setval('reservations_id_seq', (SELECT MAX(id) FROM reservations));
