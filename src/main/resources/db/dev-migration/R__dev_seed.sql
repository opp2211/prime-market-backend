truncate users cascade;
insert into users(username, email, password_hash)
values ('user1',
        'user1@123.123',
        '$2a$10$.Kk5wPXj7pUvsGonRUyKwuh4u3QdvWcABsTPaHFdd48HQe/J.Us/e'),
       ('sup1',
        'sup1@123.123',
        '$2a$10$.Kk5wPXj7pUvsGonRUyKwuh4u3QdvWcABsTPaHFdd48HQe/J.Us/e');

