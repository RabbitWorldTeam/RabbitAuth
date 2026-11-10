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

## Commit Message Format

Use `<type>: :<gitmoji>: <中文摘要>` for manually authored commit subjects. Use a lowercase type, the literal colon-delimited Gitmoji shortcode, and one space between parts. Keep the Chinese summary concise and specific to the change.

- `feat: :sparkles: 新特性`
- `fix: :bug: 修复登录提示`
- `docs: :memo: 更新安装说明`
- `refactor: :recycle: 整理命令注册`
- `style: :art: 调整物品配色`
- `test: :white_check_mark: 补充权限回归检查`
- `build: :package: 统一产物命名`
- `chore: :wrench: 更新开发配置`
