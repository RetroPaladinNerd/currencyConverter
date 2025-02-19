# Currency Converter Application

## Описание

Это Spring Boot приложение для конвертации валют, которое использует API Центрального банка Российской Федерации (ЦБ РФ) для получения актуальных курсов валют. Приложение предоставляет API для конвертации валюты из одной в другую, а также веб-интерфейс для просмотра списка доступных валют.

## Технологии

*   Java 17 
*   Spring Boot
*   Spring Web
*   Jackson (для обработки JSON)
*   Lombok
*   Gradle (для управления зависимостями и сборки проекта)

## Обмен валюты

#### GET `/convert?from=BASE_CURRENCY_CODE&to=TARGET_CURRENCY_CODE&amount=$AMOUNT`

Расчитать конвертацию определенной суммы денег из одной валюты в другую. Валютная пара и сумма
указанный в адресе запроса. Пример ответа:

```json
{
  "fromCurrency": "USD",
  "toCurrency": "RUB",
  "amount": 100.0,
  "convertedAmount": 9222.2200
}
```
### SonarCloud
https://sonarcloud.io/project/overview?id=maks2134_Finance-tracker
