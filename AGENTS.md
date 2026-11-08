# Repository Guidelines

RabbitAuth targets Java 21, Minecraft 1.21.1 and NeoForge 21.1.265.
Sources belong under src/main/java/com/tr4nce/rabbitauth/.
Use four spaces, UTF-8, PascalCase classes, camelCase members and UPPER_SNAKE_CASE constants.
Build with .\gradlew.bat build; use .\gradlew.bat runServer for manual verification.
Keep this dedicated-server mod independent of RabbitCore and client classes.
Never log passwords. Keep hashing and database I/O off the server thread with bounded queues.
Bind async results to the original connection and fail closed on storage errors.
Do not claim build success proves authentication or client compatibility.
README features remain planned until implemented and verified.
Preserve required license notices if copying CatseedLogin code.