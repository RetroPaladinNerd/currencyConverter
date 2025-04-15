## Проект "Обмен валют"

REST API для описания валют и обменных курсов. Позволяет просматривать и редактировать списки валют и обменных курсов, и совершать расчёт конвертации произвольных сумм из одной валюты в другую.

## Что нужно знать

- Java - коллекции, ООП
- Maven/Gradle
- HTTP - GET и POST запросы, коды ответа
- SQL, JDBC
- REST API, JSON

## Структура базы данных

### `banks`

| Колонка | Тип | Комментарий |
| --- | --- | --- |
| id | int | Айди банка, автоинкремент, первичный ключ |
| name | Varchar | Название банка |

### `сurrencies`

| Колонка | Тип | Комментарий |
| --- | --- | --- |
| id | int | Айди валюты, автоинкремент, первичный ключ |
| code | Varchar | Код валюты |
| name | Varchar | Имя валюты |

### `exchange_rates`

| Колонка | Тип | Комментарий |
| --- | --- | --- |
| id | int | Айди курса обмена, автоинкремент, первичный ключ |
| bank_id | int | Айди банка в котором совершается обмен |
| from_currency_code | Varchar | Код базовой валюты |
| to_currency_code | Varchar | Код целевой валюты |
| Rate | Decimal(4) | Курс обмена единицы базовой валюты к единице целевой валюты |

`Decimal(4)` - десятичное число с 4 знаками после запятой. Полезно для валют, отличающихся на порядки.

### Валюты

#### GET `/currencies`

Получение списка валют. Пример ответа:
```
[
    "currency": {
        "id": 0,
        "code": "USD",
        "name": "United States dollar",
    },   
    "currency": {
        "id": 0,
        "code": "EUR",
        "name": "Euro",
    }
]
```

HTTP коды ответов:
- Успех - 200
- Ошибка (например, база данных недоступна) - 500

#### POST `/currencies?code=XXX&name=YYY`

Добавление новой валюты в базу. Пример ответа.
```
    {
        "id": 0,
        "code": "EUR",
        "name": "Euro",
    }
```

HTTP коды ответов:
- Успех - 200
- Отсутствует нужное поле формы - 400
- Ошибка (например, база данных недоступна) - 500

### Обменные курсы

#### GET `/exchange-rates`

Получение списка всех обменных курсов. Пример ответа:
```
[
    {
        "id": 9,
        "rate": 3.0800,
        "fromCurrencyCode": "USD",
        "toCurrencyCode": "BYN"
    },
    {
        "id": 10,
        "rate": 3.1200,
        "fromCurrencyCode": "USD",
        "toCurrencyCode": "BYN"
    },
    {
        "id": 11,
        "rate": 3.0700,
        "fromCurrencyCode": "USD",
        "toCurrencyCode": "BYN"
    }
]
```

HTTP коды ответов:
- Успех - 200
- Ошибка (например, база данных недоступна) - 500

#### GET `/exchange-rates/9`

Получение конкретного обменного курса. Валютная пара задаётся идущими подряд кодами валют в адресе запроса. Пример ответа:
```
    {
        "id": 9,
        "rate": 3.0800,
        "fromCurrencyCode": "USD",
        "toCurrencyCode": "BYN"
    }

```

HTTP коды ответов:
- Успех - 200
- Коды валют пары отсутствуют в адресе - 400
- Обменный курс для пары не найден - 404
- Ошибка (например, база данных недоступна) - 500

#### POST `/exchange-rates?bankId=XXX&fromCurrencyCode=YYY&toCurrencyCode=ZZZ&rate=RRR`

Добавление нового обменного курса в базу. Пример ответа.

- `bankid` - 3
- `fromCurrencyCode` - USD
- `toCurrencyCode` - EUR
- `rate` - 0.99

Пример ответа - JSON представление вставленной в базу записи, включая её ID:
```
{
    "id": 40,
    "rate": 0.25,
    "fromCurrencyCode": "PLN",
    "toCurrencyCode": "BYN"
}
```

HTTP коды ответов:
- Успех - 200
- Отсутствует нужное поле формы - 400
- Ошибка (например, база данных недоступна) - 500

### Обмен валюты

#### GET `currencies/convert?bankId=XXX&fromCurrencyCode=YYY&toCurrencyCode=ZZZ&amount=RRR`

Рассчёт перевода определённого количества средств из одной валюты в другую. Пример запроса - POST `currencies/convert?bankId=2&fromCurrencyCode=RUB&toCurrencyCode=BYN&amount=100`.

Пример ответа:
```
{
    "fromCurrency": "USD",
    "toCurrency": "BYN",
    "amount": 100.00,
    "convertedAmount": 308.000000,
    "exchangeRate": 3.0800
}
```

### Sonar

* https://sonarcloud.io/project/overview?id=RetroPaladinNerd_currencyConverter
