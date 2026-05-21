# Task Manager Server v0.7

## Описание

REST API сервер для управления проектами и задачами. В проекте реализованы регистрация и вход пользователей, JWT access/refresh токены, хранение refresh-сессий, ролевая модель доступа, подключение к PostgreSQL и настройка HTTPS через переменные окружения.

Репозиторий содержит только серверную часть приложения.

## Стек технологий

- Java 21
- Spring Boot 4.0.3
- Spring Web MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- H2 для тестов
- JWT на HMAC SHA-256
- HTTPS/SSL
- Maven Wrapper
- GitHub Actions

## Сущности

### User

- `id` - уникальный идентификатор пользователя
- `username` - имя пользователя, уникальное
- `email` - электронная почта, уникальная
- `passwordHash` - BCrypt-хеш пароля
- `role` - роль пользователя: `USER`, `MANAGER`, `ADMIN`

### UserSession

- `id` - уникальный идентификатор сессии
- `user` - пользователь, которому принадлежит сессия
- `refreshTokenId` - идентификатор refresh token
- `status` - статус сессии: `ACTIVE`, `REFRESHED`, `EXPIRED`, `REVOKED`
- `createdAt` - дата создания сессии
- `expiresAt` - дата истечения refresh-сессии
- `updatedAt` - дата последнего изменения

### Project

- `id` - уникальный идентификатор проекта
- `name` - название проекта, уникальное
- `description` - описание проекта

### Task

- `id` - уникальный идентификатор задачи
- `title` - название задачи
- `description` - описание задачи
- `status` - статус задачи: `OPEN`, `IN_PROGRESS`, `DONE`
- `project` - проект, к которому относится задача
- `assignee` - назначенный пользователь
- `tags` - набор тегов задачи
- `createdAt` - дата создания

### Tag

- `id` - уникальный идентификатор тега
- `name` - название тега, уникальное
- `color` - цвет тега

### Comment

- `id` - уникальный идентификатор комментария
- `task` - задача, к которой относится комментарий
- `author` - автор комментария
- `content` - текст комментария
- `createdAt` - дата создания

### Login

- `username` - имя пользователя или email
- `password` - пароль пользователя

### Registration

- `username` - имя пользователя
- `email` - электронная почта
- `password` - пароль пользователя

## API Endpoints

### Authentication

- `POST /api/auth/register` - регистрация пользователя
- `POST /api/auth/login` - вход пользователя и получение пары JWT токенов
- `POST /api/auth/refresh` - обновление access/refresh токенов

### Users

- `POST /api/users` - создать пользователя, доступно только `ADMIN`
- `GET /api/users` - получить список пользователей, доступно только `ADMIN`
- `GET /api/users/{id}` - получить пользователя по id, доступно только `ADMIN`
- `PUT /api/users/{id}` - изменить пользователя или его роль, доступно только `ADMIN`
- `DELETE /api/users/{id}` - удалить пользователя, доступно только `ADMIN`

### Projects

- `POST /api/projects` - создать проект, доступно `MANAGER` и `ADMIN`
- `GET /api/projects` - получить список проектов
- `GET /api/projects/{id}` - получить проект по id
- `PUT /api/projects/{id}` - изменить проект, доступно `MANAGER` и `ADMIN`
- `DELETE /api/projects/{id}` - удалить проект, доступно только `ADMIN`

### Tasks

- `POST /api/tasks` - создать задачу, доступно `MANAGER` и `ADMIN`
- `GET /api/tasks` - получить список задач
- `GET /api/tasks/{id}` - получить задачу по id
- `PUT /api/tasks/{id}` - изменить задачу, доступно `MANAGER` и `ADMIN`
- `DELETE /api/tasks/{id}` - удалить задачу, доступно `MANAGER` и `ADMIN`

### Tags

- `POST /api/tags` - создать тег, доступно `MANAGER` и `ADMIN`
- `GET /api/tags` - получить список тегов
- `GET /api/tags/{id}` - получить тег по id
- `PUT /api/tags/{id}` - изменить тег, доступно `MANAGER` и `ADMIN`
- `DELETE /api/tags/{id}` - удалить тег, доступно только `ADMIN`

### Comments

- `POST /api/comments` - создать комментарий
- `GET /api/comments` - получить список комментариев
- `GET /api/comments/{id}` - получить комментарий по id
- `PUT /api/comments/{id}` - изменить комментарий, доступно `MANAGER` и `ADMIN`
- `DELETE /api/comments/{id}` - удалить комментарий, доступно `MANAGER` и `ADMIN`

### Business Operations

- `POST /api/operations/tasks/{taskId}/tags/{tagId}` - добавить тег к задаче, доступно `MANAGER` и `ADMIN`
- `DELETE /api/operations/tasks/{taskId}/tags/{tagId}` - удалить тег у задачи, доступно `MANAGER` и `ADMIN`
- `POST /api/operations/tasks/{taskId}/comments` - добавить комментарий к задаче
- `GET /api/operations/projects/{projectId}/summary` - получить сводку по задачам проекта

## Проверки и JWT токены

### Требования к паролю

- минимальная длина: 8 символов
- минимум одна цифра
- минимум один специальный символ

### Ограничения при регистрации

- `username` должен быть уникальным
- `email` должен быть уникальным
- пароль сохраняется только в виде BCrypt-хеша
- первый зарегистрированный пользователь получает роль `ADMIN`
- следующие зарегистрированные пользователи получают роль `USER`

### JWT токены

- `accessToken` используется для доступа к защищённым endpoint'ам
- `refreshToken` используется для обновления пары токенов
- тип токена хранится в claim `type`: `access` или `refresh`
- access token содержит роль пользователя и идентификатор refresh-сессии
- refresh token связан с записью в таблице `user_sessions`
- повторное использование старого refresh token запрещено, потому что старая сессия получает статус `REFRESHED`
- время жизни access token по умолчанию: 900 секунд
- время жизни refresh token по умолчанию: 604800 секунд

Для запросов к защищённым endpoint'ам используется заголовок:

```http
Authorization: Bearer <accessToken>
```

API работает в stateless-режиме. HTTP-сессии не создаются, CSRF-защита отключена, так как доступ выполняется через Bearer token.

## Ролевая модель

- `USER` - базовая роль, может читать основные данные и создавать комментарии
- `MANAGER` - может управлять проектами, задачами, тегами и бизнес-операциями
- `ADMIN` - полный доступ, включая управление пользователями и удаление защищённых справочников

## PostgreSQL и секреты

Пример создания базы:

```sql
CREATE USER task_user WITH PASSWORD 'task_password';
CREATE DATABASE task_manager OWNER task_user;
GRANT ALL PRIVILEGES ON DATABASE task_manager TO task_user;
```

Переменные окружения для запуска:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/task_manager"
$env:DB_USERNAME="task_user"
$env:DB_PASSWORD="task_password"
$env:JWT_SECRET="change-this-secret-to-a-long-random-value"
$env:SERVER_SSL_ENABLED="false"
```

Шаблон переменных находится в `.env.example`. Реальные пароли, JWT secret и keystore password не хранятся в репозитории.

## HTTPS/SSL

Для локальной проверки HTTPS используется цепочка сертификатов, которую создаёт скрипт:

```powershell
$env:TLS_KEYSTORE_PASSWORD="local-password"
.\scripts\generate-tls-chain.ps1 -StudentId "1BIB23219"
```

Переменные для запуска с HTTPS:

```powershell
$env:SERVER_SSL_ENABLED="true"
$env:SERVER_SSL_KEY_STORE="certs/local/rbpo-service-1BIB23219.p12"
$env:SERVER_SSL_KEY_STORE_PASSWORD="local-password"
$env:SERVER_SSL_KEY_STORE_TYPE="PKCS12"
$env:SERVER_SSL_KEY_ALIAS="rbpo-service-1bib23219"
```

После включения SSL API доступен по адресу:

```text
https://localhost:8080
```

Локальные сертификаты, keystore, CSR и приватные ключи игнорируются через `.gitignore`.

## Запуск

```powershell
.\mvnw.cmd spring-boot:run
```

После успешного старта API доступен по адресу:

```text
http://localhost:8080
```

Если включён HTTPS, используется адрес:

```text
https://localhost:8080
```

## Проверка через Postman

1. `POST /api/auth/register` - зарегистрировать пользователя.
2. `POST /api/auth/login` - получить `accessToken` и `refreshToken`.
3. `GET /api/projects` - выполнить запрос с `Authorization: Bearer <accessToken>`.
4. `POST /api/auth/refresh` - получить новую пару токенов по refresh token.

Пример тела регистрации:

```json
{
  "username": "admin",
  "email": "admin@example.com",
  "password": "Strong#123"
}
```

Пример тела входа:

```json
{
  "username": "admin",
  "password": "Strong#123"
}
```

Пример тела refresh-запроса:

```json
{
  "refreshToken": "<refreshToken>"
}
```

## Тесты и сборка

Запуск тестов:

```powershell
.\mvnw.cmd clean test
```

Сборка jar:

```powershell
.\mvnw.cmd package -DskipTests
```

Собранный файл находится в `target/demo-0.0.1-SNAPSHOT.jar`.

## Pipeline

Файл pipeline: `.github/workflows/ci.yml`.

Шаги pipeline:

- checkout репозитория
- установка JDK 21
- компиляция проекта
- запуск тестов
- сборка jar
- публикация jar как GitHub Actions artifact

Для CI используются GitHub Secrets:

- `APP_KEYSTORE_BASE64` - keystore в Base64
- `APP_KEYSTORE_PASSWORD` - пароль от keystore

## UML и ER

UML - стандартный язык графического моделирования. Он нужен, чтобы описывать структуру приложения и поведение системы понятными для команды диаграммами.

Основные типы UML-диаграмм:

- диаграмма классов - структура классов и связей
- диаграмма компонентов - модули системы и зависимости
- диаграмма вариантов использования - акторы и сценарии
- диаграмма последовательности - обмен сообщениями во времени
- диаграмма активностей - процесс и ветвления

ER-диаграмма описывает данные: сущности, атрибуты, ключи и связи. Для этого проекта ER-модель включает таблицы `projects`, `tasks`, `tags`, `task_tags`, `task_comments`, `app_users` и `user_sessions`.

Связи данных:

- один проект содержит много задач
- одна задача относится к одному проекту
- задача может быть назначена одному пользователю
- пользователь может иметь много refresh-сессий
- задача может иметь много комментариев
- пользователь может написать много комментариев
- задачи и теги связаны отношением многие-ко-многим через `task_tags`

## Лабораторная работа 2: API лицензий

В проект добавлена модель данных PostgreSQL/JPA и REST-операции для управления лицензиями.

### Таблицы и связи

- `licenses` хранит ключ лицензии, пользователя, статус, дату активации, дату истечения, флаг блокировки, срок действия и лимит устройств.
- `devices` хранит клиентские устройства по нормализованному идентификатору устройства.
- `device_licenses` связывает лицензии и устройства, а также хранит дату активации. Пара `(license_id, device_id)` уникальна.
- `license_history` хранит историю операций с лицензией: `CREATED`, `ACTIVATED`, `CHECKED`, `RENEWED`.
- `licenses.user_id` ссылается на `app_users.id`.
- `licenses.device_id` ссылается на `devices.id`.
- `device_licenses.license_id` ссылается на `licenses.id`.
- `device_licenses.device_id` ссылается на `devices.id`.
- `license_history.license_id` ссылается на `licenses.id`.

### Endpoints лицензий

Создание лицензии. Требуется роль `MANAGER` или `ADMIN`.

```http
POST /api/licenses
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "userId": 1,
  "durationDays": 30,
  "deviceLimit": 1
}
```

Активация лицензии на устройстве.

```http
POST /api/licenses/activate
Content-Type: application/json
```

```json
{
  "licenseKey": "LIC-...",
  "deviceFingerprint": "AA-BB-CC-01",
  "deviceName": "Developer laptop"
}
```

Проверка лицензии на активированном устройстве.

```http
POST /api/licenses/check
Content-Type: application/json
```

```json
{
  "licenseKey": "LIC-...",
  "deviceFingerprint": "AA-BB-CC-01"
}
```

Продление лицензии. Требуется роль `MANAGER` или `ADMIN`.

```http
POST /api/licenses/{licenseKey}/renew
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "additionalDays": 15
}
```

### Ответ TicketResponse

Операции активации, проверки и продления возвращают `TicketResponse`.

```json
{
  "ticket": {
    "serverDate": "2026-05-20T21:00:00",
    "ticketTtlSeconds": 300,
    "licenseActivatedAt": "2026-05-20T21:00:00",
    "licenseExpiresAt": "2026-06-19T21:00:00",
    "userId": 1,
    "deviceId": 1,
    "blocked": false
  },
  "signature": "base64-signature"
}
```

Тикет подписывается модулем ЭЦП алгоритмом `SHA256withRSA`. Перед подписанием объект `ticket` приводится к каноническому JSON, затем подпись кодируется в Base64.

Настройки ЭЦП:

```properties
signature.key-store-path=${SIGNATURE_KEY_STORE_PATH}
signature.key-store-password=${SIGNATURE_KEY_STORE_PASSWORD}
signature.key-store-type=${SIGNATURE_KEY_STORE_TYPE:PKCS12}
signature.key-alias=${SIGNATURE_KEY_ALIAS:ticket-signing}
signature.key-password=${SIGNATURE_KEY_PASSWORD}
signature.algorithm=${SIGNATURE_ALGORITHM:SHA256withRSA}
```

Локальное хранилище и публичный сертификат можно создать командой:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\generate-signing-keystore.ps1 -Alias ticket-signing -StorePath certs/local/ticket-signing.p12 -CertificatePath certs/local/ticket-signing.cer -StorePassword changeit -KeyPassword changeit
```

Для CI/CD keystore хранится в GitHub Secrets: `SIGNATURE_KEYSTORE_BASE64`, `SIGNATURE_KEYSTORE_PASSWORD`, `SIGNATURE_KEY_PASSWORD`, `SIGNATURE_KEY_ALIAS`.

### Порядок проверки в Postman

1. Зарегистрировать первого пользователя через `POST /api/auth/register`.
2. Выполнить вход через `POST /api/auth/login` и скопировать `accessToken`.
3. Создать лицензию через `POST /api/licenses`.
4. Скопировать `licenseKey` из ответа.
5. Активировать лицензию через `POST /api/licenses/activate`.
6. Проверить лицензию через `POST /api/licenses/check`.
7. Продлить лицензию через `POST /api/licenses/{licenseKey}/renew`.
8. Попробовать активировать второе устройство при `deviceLimit = 1`; API должен вернуть ошибку `Device limit reached`.

## Лабораторная работа 4: антивирусные сигнатуры

Добавлен модуль управления антивирусными сигнатурами. Используются таблицы `signatures`, `signatures_history` и `signatures_audit`; история и аудит связаны с основной сигнатурой по `signature_id`.

Основные операции:

- `POST /api/signatures` - создать сигнатуру, доступно `ADMIN`.
- `PUT /api/signatures/{id}` - обновить сигнатуру, доступно `ADMIN`.
- `DELETE /api/signatures/{id}` - логически удалить сигнатуру, доступно `ADMIN`.
- `GET /api/signatures` - получить полную базу без `DELETED`.
- `GET /api/signatures/increment?since=2026-05-21T00:00:00Z` - получить инкремент, включая `DELETED`.
- `POST /api/signatures/by-ids` - получить сигнатуры по списку UUID.
- `GET /api/signatures/{id}/history` - получить историю сигнатуры.
- `GET /api/signatures/{id}/audit` - получить аудит сигнатуры.

Подпись формируется через `DigitalSignatureService`. При `create` и `update` подпись пересчитывается, при `update` и `delete` сохраняется предыдущая версия в history, а `create/update/delete` пишут запись в audit.

Пример тела запроса:

```json
{
  "threatName": "Trojan.Sample",
  "firstBytesHex": "4D5A",
  "remainderHashHex": "AABBCCDD",
  "remainderLength": 4,
  "fileType": "exe",
  "offsetStart": 0,
  "offsetEnd": 12
}
```

Проверка:

```powershell
.\mvnw.cmd test
```
