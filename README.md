# prime-market-backend

Бэкенд Prime Market. Приложение на Spring Boot с PostgreSQL, Flyway и JWT-аутентификацией.

Prime Market - сайт-биржа игровых ценностей, где пользователи могут купить и продать товары или услуги, а также 
выставить свое предложение на продажу или покупку.

## Стек
- Java 21 + Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- Spring Security (JWT)
- Flyway
- PostgreSQL
- Maven

## API contract
Коммитящийся OpenAPI contract лежит в `api-contract/openapi.json`.

Обновление контракта:
- Windows: `mvnw.cmd -Popenapi-contract test`
- Unix-like: `./mvnw -Popenapi-contract test`

Команда поднимает Spring-приложение в тестовом контуре, забирает актуальный `/v3/api-docs` и перезаписывает артефакт в репозитории.
