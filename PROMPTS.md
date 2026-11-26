# Использование ИИ

- ИИ использовался для упрощения автотестов, настройки инструментов, помощи при подготовки документации и дополнительном анализе Postman-коллекции;
- Результаты проверял запуском тестов, ручными запросами через Postman и анализом логов.

## Основные промпты и результаты

| Запрос                                                                                              | Результат                           |
|-----------------------------------------------------------------------------------------------------|-------------------------------------|
| Help me structure the table in .md format for creating test cases                                   | Черновик таблицы с тест-кейсами     |
| Analyze the Postman collection in more detail and check whether my test cases are properly designed | TESTCASES.md                        |
| Analyze the correctness of the automated test writing, and suggest improvements if possible.                               | ApiClient, AuthTest, ItemTest       |
| Please help analyze this log for any errors that occurred during the process                                               | Диагностика ошибок, BUGS.md         |
| Help me set up Maven Wrapper and Allure                                                                   | Команды запуска и пример отчёта     |
| Help me set up Checkstyle and Spotless                                                                    | Конфигурации и команды проверки кода |