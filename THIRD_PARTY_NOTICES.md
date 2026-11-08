# Third-party dependencies

RabbitAuth source is licensed under MIT. Bundled libraries retain their own copyright and license notices inside their original nested JARs.

| Library | Bundled version | Project |
| --- | --- | --- |
| Bouncy Castle bcprov-jdk18on | 1.80 | https://www.bouncycastle.org/ |
| Xerial sqlite-jdbc | 3.49.1.0 | https://github.com/xerial/sqlite-jdbc |
| MySQL Connector/J | 9.2.0 | https://github.com/mysql/mysql-connector-j |

No CatseedLogin source files are bundled. This project implements its own NeoForge integration and password format using Bouncy Castle Argon2id.
