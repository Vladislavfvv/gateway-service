# Инструкция по запуску и проверке всех сервисов

## Предварительные требования

1. **Docker Desktop** должен быть запущен
2. **Maven** установлен (для локальной сборки, если нужно)
3. Все сервисы находятся в правильных директориях

## Шаг 1: Запуск всех сервисов

Перейдите в директорию gateway-service:

```bash
cd D:\JAVA\Innowise\Projects\28-11-2025\gateway-service\gateway-service
```

Запустите все сервисы:

```bash
docker-compose up -d --build
```

Флаг `-d` запускает контейнеры в фоновом режиме.
Флаг `--build` пересобирает образы перед запуском.

## Шаг 2: Проверка статуса контейнеров

Проверьте, что все контейнеры запущены:

```bash
docker-compose ps
```

Или через Docker:

```bash
docker ps
```

Должны быть запущены следующие контейнеры:
- `postgres-auth` (порт 5433)
- `postgres-user` (порт 5432)
- `postgres-order` (порт 5434)
- `redis` (порт 6379)
- `authentication-service` (порт 8081)
- `user-service` (порт 8082)
- `order-service` (порт 8083)
- `api-gateway` (порт 8084)

## Шаг 4: Просмотр логов

Просмотр логов всех сервисов:

```bash
docker-compose logs -f
```

Просмотр логов конкретного сервиса:

```bash
# Authentication Service
docker-compose logs -f authentication-service

# User Service
docker-compose logs -f user-service

# Order Service
docker-compose logs -f order-service

# API Gateway
docker-compose logs -f api-gateway
```

## Шаг 5: Проверка работы сервисов

### 5.1. Проверка баз данных

```bash
# PostgreSQL Auth
docker exec -it postgres-auth psql -U postgres -d auth_db -c "SELECT version();"

# PostgreSQL User
docker exec -it postgres-user psql -U postgres -d us_db -c "SELECT version();"

# PostgreSQL Order
docker exec -it postgres-order psql -U postgres -d os_db -c "SELECT version();"

# Redis
docker exec -it redis redis-cli ping
# Должен вернуть: PONG
```

### 5.2. Проверка Authentication Service

```bash
# Проверка доступности (должен вернуть 404 или 401, но не ошибку подключения)
curl http://localhost:8081/actuator/health

# Или через браузер
# http://localhost:8081/actuator/health
```

### 5.3. Проверка User Service

```bash
# Проверка доступности
curl http://localhost:8082/actuator/health

# Или через браузер
# http://localhost:8082/actuator/health
```

### 5.4. Проверка Order Service

```bash
# Проверка доступности
curl http://localhost:8083/actuator/health

# Или через браузер
# http://localhost:8083/actuator/health
```

### 5.5. Проверка API Gateway

```bash
# Проверка доступности
curl http://localhost:8084/actuator/health

# Или через браузер
# http://localhost:8084/actuator/health
```

## Шаг 6: Тестирование полного flow регистрации пользователя

### 6.1. Регистрация credentials

```bash
curl -X POST http://localhost:8084/auth/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "login": "test@example.com",
    "password": "password123",
    "role": "ROLE_USER"
  }'
```

**Ожидаемый ответ:**
```json
{
  "message": "User registered successfully. Please login to get tokens."
}
```

### 6.2. Логин для получения токена

```bash
curl -X POST http://localhost:8084/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "login": "test@example.com",
    "password": "password123"
  }'
```

**Ожидаемый ответ:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "type": "Bearer",
  "expiresIn": 900000
}
```

**Сохраните `accessToken` для следующих шагов!**

### 6.3. Создание профиля пользователя

```bash
# Замените YOUR_TOKEN на токен из предыдущего шага
curl -X POST http://localhost:8084/auth/v1/createUser \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "firstName": "Ivan",
    "lastName": "Ivanov",
    "birthDate": "1920-01-01"
  }'
```

**Ожидаемый ответ:**
```json
{
  "id": 1,
  "email": "test@example.com",
  "firstName": "Ivan",
  "lastName": "Ivanov",
  "birthDate": "1920-01-01",
  "cards": []
}
```

### 6.4 Просмотреть информацио о пользователе и его картах
# Замените YOUR_TOKEN на токен из предыдущего шага 
```bash
Auth-> Auth Type: Bearer Token Например: Ваш_токен!!!):

      --  посмотреть информацию о пользователе:
        GET http://localhost:8084/api/v1/users/self

      --  дополнить недостающую информацию о пользователе:
        PUT http://localhost:8084/api/v1/users
        Например
        {           
          "cards": [  
            {
              "userId": 26, <- это можно не указывать, т.к. Id берется следующий за последним в БД
              "number": "2533567812341375",
              "holder": "someName someLastName", это можно не указывать, т.к. вставляется name + surname
              "expirationDate": "2026-12-31"
            },
            {
              "userId": 26,
              "number": "3533432187654472",
              "holder": "someName someLastName",
              "expirationDate": "2027-06-30"
            }
          ]
        }

      --  посмотреть информацию о картах пользователя:
        GET http://localhost:8084/api/v1/cards
        Выведет результат: в виде json карты пользователя
```

    8)  Работа с заказами(пользователь работает только со своими заказами)

    - Создать заказ:
```bash
    POST http://localhost:8084/api/v1/orders
    {
        "items": [
            {
                "itemId": 1,
                "quantity": 2.0
            },
            {
                "itemId": 3,
                "quantity": 1.5
            }
        ]
    }
```
    8.1 Просмотреть товары 
```bash
    GET http://localhost:8084/api/v1/items
    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

    Ответ:
    [
        {
            "id": 1,
            "name": "Laptop",
            "price": 1500.00
        },
        {
            "id": 2,
            "name": "Mouse",
            "price": 25.50
        }
    ]
```

    8.2 Получить свои заказы 
```bash
    `GET http://localhost:8084/api/v1/orders/my`
    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
``` 

    8.3 Получить заказ по ID
```bash
    GET GET http://localhost:8084/api/v1/orders/{id}
```

    Пример:
```bash
    GET http://localhost:8084/api/v1/orders/1
    Authorization: Bearer {ваш_JWT_токен}

    Ответ:
    [
    {
        "order": {
            "id": 2,
            "userId": 2,
            "status": "NEW",
            "creationDate": "2025-12-01T07:03:46.465754",
            "itemDtoList": [
                {
                    "id": 3,
                    "itemId": 1,
                    "orderId": 2,
                    "quantity": 2.00
                },
                {
                    "id": 4,
                    "itemId": 2,
                    "orderId": 2,
                    "quantity": 3.00
                }
            ]
        },
        "user": {
            "id": 2,
            "firstName": "User",
            "lastName": "lastName",
            "birthDate": "1925-01-01",
            "email": "testUser@example.com"
        }
    }
    ]
```
    8.4 Получить товары по IDs
```bash
    http://localhost:8084/api/v1/orders/ids?ids=2&ids=3
    Authorization: Bearer {ваш_JWT_токен}
    Ответ: выведутся все заказы пользователя
```

    8.5 Получить заказы по статусам
```bash
    GET http://localhost:8084/api/v1/orders/statuses
    **Доступные статусы:** `NEW`, `PROCESSING`, `COMPLETED`, `CANCELLED`
```
    Пример:
    **Правильный способ в Postman:**
    1. Введите URL: `http://localhost:8084/api/v1/orders/statuses`
    2. Перейдите на вкладку **"Params"**
    3. Добавьте параметр `statuses` со значением `NEW`
    4. Добавьте еще один параметр `statuses` со значением `PROCESSING`

    **Или вручную в URL:**
    ```
    GET http://localhost:8084/api/v1/orders/statuses?statuses=NEW&statuses=PROCESSING
    Authorization: Bearer {ваш_JWT_токен}
    ```

    8.6 Обновить статус заказа:
    Пример:
```bash
    PUT http://localhost:8084/api/v1/orders/2
    {  
        "status": "PROCESSING"
    }
    Ответ:
    [
        {
            "order": {
                "id": 2,
                "userId": 2,
                "status": "PROCESSING",
                "creationDate": "2025-12-01T07:03:46.465754",
                "itemDtoList": [
                    {
                        "id": 3,
                        "itemId": 1,
                        "orderId": 2,
                        "quantity": 2.00
                    },
                    {
                        "id": 4,
                        "itemId": 2,
                        "orderId": 2,
                        "quantity": 3.00
                    }
                ]
            },
            "user": {
                "id": 2,
                "firstName": "User",
                "lastName": "lastName",
                "birthDate": "1925-01-01",
                "email": "testUser@example.com"
            }
        }
    ]
```

### 9. Roadmap работы программы **ROLE_ADMIN**:

    9.1 Авторизоваться через 
```bash
    `POST http://localhost:8084/auth/v1/login`:
    Content-Type: application/json
    {
      "login": "admin@tut.by",
      "password": "admin"
    }
```   
    **Ожидаемый результат**: JSON с `accessToken` и `refreshToken`

    9.2 Копируем полученный токен из поля access_token:
    Например: Ваш_токен  

    9.3 Далее варианты(не забываем в каждом запросе постоянно вставлять Auth-> Auth Type: Bearer Token Например: Ваш_токен!!!):
    ROLE_ADMIN предоставлен широкий спектр возможностей:

    9.4 посмотреть всех юзеров пагинация:
    GET http://localhost:8084/api/v1/users?page=4&size=5

    9.5 посмотреть все карты пагинация:
    GET http://localhost:8084/api/v1/cards?page=4&size=10

    9.6 редактирование пользователя
    PUT http://localhost:8084/api/v1/users/30
    Например:
    raw: x-www-form-urlencoded
    { 
      "birthDate": "2002-01-01",
      "email": "newuser15@example.com",
      "cards": [
        {
          "userId": 30,
          "number": "2933555855349975",
          "holder": "newUser10 newUser10",
          "expirationDate": "2026-12-31"
        },
        {
          "userId": 30,
          "number": "1933465667699472",
          "holder": "newUser10 newUser10",
          "expirationDate": "2027-06-30"
        }
      ]
    }

    9.7 Удаление пользователя:
    DELETE http://localhost:8084/api/v1/users/28

    9.8 Создать товар:
    POST http://localhost:8084/api/v1/items
     Body: { 
            "name": "Keyboard", 
            "price": 75.00 
        }

    9.9 Редактировать товар:
    PUT http://localhost:8084/api/v1/items/1
    Authorization: Bearer {ADMIN_TOKEN}
    Content-Type: application/json
    {
        "name": "Phone Pro",
        "price": 650.00
    }

     9.10 Редактировать статус заказа:
    PUT http://localhost:8084/api/v1/orders/2
    Authorization: Bearer {ADMIN_TOKEN}
    Content-Type: application/json
    {
       "status": "PROCESSING"
    }  

    9.11 Удалить товар:
    DELETE http://localhost:8084/api/v1/items/1
    Authorization: Bearer {ADMIN_TOKEN}

    9.12 Получить все заказы:
    http://localhost:8084/api/v1/orders

    9.13 Получить заказы по статусам:
    GET http://localhost:8084/api/v1/orders/statuses?statuses=NEW&statuses=PROCESSING

    9.14 Удалить заказ:
    DELETE http://localhost:8084/api/v1/orders/1








## Шаг 7: Проверка работы через Gateway

### 7.1. Проверка маршрутизации к User Service

```bash
# Получение пользователя по email (требует JWT токен)
curl -X GET "http://localhost:8084/api/v1/users/email?email=test@example.com" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 7.2. Проверка маршрутизации к Order Service

```bash
# Создание заказа (требует JWT токен)
curl -X POST http://localhost:8084/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "items": [
      {
        "name": "Test Item",
        "price": 100.0,
        "quantity": 2
      }
    ]
  }'
```

## Шаг 8: Остановка сервисов

```bash
# Остановка всех контейнеров
docker-compose down

# Остановка с удалением volumes (удалит все данные в БД!)
docker-compose down -v
```

## Шаг 9: Перезапуск сервисов

```bash
# Перезапуск всех сервисов
docker-compose restart

# Перезапуск конкретного сервиса
docker-compose restart api-gateway
```

## Возможные проблемы и решения

### Проблема: Контейнеры не запускаются

**Решение:**
1. Проверьте логи: `docker-compose logs`
2. Убедитесь, что Docker Desktop запущен
3. Проверьте, что порты не заняты другими приложениями

### Проблема: Ошибка "network backend-network not found"

**Решение:**
```bash
docker network create backend-network
```

### Проблема: Ошибка сборки образов

**Решение:**
1. Проверьте пути в `docker-compose.yml` (context)
2. Убедитесь, что все сервисы находятся в правильных директориях
3. Попробуйте собрать образы отдельно:
   ```bash
   docker-compose build --no-cache
   ```

### Проблема: Сервисы не могут подключиться друг к другу

**Решение:**
1. Проверьте, что все сервисы в одной сети: `docker network inspect backend-network`
2. Используйте имена контейнеров для подключения (не localhost)
3. Проверьте переменные окружения в docker-compose.yml

## Полезные команды

```bash
# Просмотр использования ресурсов
docker stats

# Просмотр логов последних 100 строк
docker-compose logs --tail=100

# Выполнение команды в контейнере
docker exec -it api-gateway sh

# Просмотр переменных окружения контейнера
docker exec api-gateway env

# Очистка неиспользуемых ресурсов Docker
docker system prune -a
```

## Порты сервисов

| Сервис | Порт | URL |
|--------|------|-----|
| Authentication Service | 8081 | http://localhost:8081 |
| User Service | 8082 | http://localhost:8082 |
| Order Service | 8083 | http://localhost:8083 |
| API Gateway | 8084 | http://localhost:8084 |
| PostgreSQL Auth | 5433 | localhost:5433 |
| PostgreSQL User | 5432 | localhost:5432 |
| PostgreSQL Order | 5434 | localhost:5434 |
| Redis | 6379 | localhost:6379 |


## Полное удаление всех ресурсов (контейнеры, образы, volume, БД и сеть)> 
ВНИМАНИЕ: эти команды удалят **все данные** в базах и кешах этого проекта.

### 1. Остановка и удаление контейнеров + volumes проекта
Из директории `gateway-service/gateway-service`:
docker-compose down -v

### 2. Удаление образов, собранных этим docker-compose
docker rmi api-gateway authentication-service user-service order-service

Если какие-то образы заняты, сначала остановите/удалите соответствующие контейнеры (`docker ps`, `docker rm`).

### 3. Удаление volumes (если по какой-то причине они остались)
docker volume rm auth_db_data us_db_data os_db_data redis_data
Посмотреть все volumes:
docker volume ls

### 4. Удаление Docker-сети проекта
docker network rm backend-network
Посмотреть все сети:docker network ls

### 5. (Опционально) Глобальная очистка неиспользуемых ресурсов Docker
docker system prune -a
Это удалит **все неиспользуемые** образы/контейнеры/сети/кеши, не только этого проекта.



Для обычных пользователей:
PUT http://localhost:8082/api/v1/users/meAuthorization: Bearer <token>{  "firstName": "John",  "lastName": "Doe",  "birthDate": "1990-01-01"}
Для админов (обновление любого пользователя):
PUT http://localhost:8082/api/v1/users/2Authorization: Bearer <admin_token>{  "firstName": "Jane",  "lastName": "Smith"}