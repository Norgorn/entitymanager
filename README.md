# locksmanager

Some test task to create locks manager.
 - Lock some id
 - Unlock some id
 - Check for deadlocks

Что сделано основные пункты + доп. задания I - IV. Тесты исключительно базовые. Логирование не настроено.
Было сделано предположение, что на каждый тип entity создаётся отдельный локер.
По идее, было бы намного удобнее защищённый код передавать прямо в метод lock, unlock вообще не делать, но тогда половина задания не имеет смысла - при таком подходе само собой выходит, что неудобно блокировать больше одной сущности в один момент времени, нет дедлоков и многих других проблем, но недоступна массовая блокировка сущностей.
