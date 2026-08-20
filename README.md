# WiFi Tester

Aplicativo Android para **auditoria local e autorizada de redes Wi‑Fi**.

## O que o app faz

- Lista redes Wi‑Fi visíveis ao aparelho.
- Mostra intensidade do sinal em dBm.
- Identifica banda de 2,4 GHz, 5 GHz ou 6 GHz.
- Mostra canal e frequência.
- Classifica a proteção da rede como WPA3, WPA2, WPA, WEP, OWE ou aberta.
- Exibe uma nota simples de segurança e uma recomendação.
- Mostra informações da conexão Wi‑Fi atual, como sinal, velocidade do link, IPv4 e DNS.
- Processa tudo localmente no aparelho.

## O que ele não faz

O WiFi Tester **não tenta descobrir senhas, não executa força bruta, não explora roteadores e não força conexão com redes**. Ele foi feito para analisar redes próprias ou redes nas quais você tem autorização para realizar testes.

## APK pelo GitHub Actions

O repositório possui o workflow `.github/workflows/build-apk.yml`.

Quando houver um push ou pull request, o GitHub compila automaticamente o APK de debug.

Para baixar pelo celular:

1. Abra a aba **Actions** deste repositório.
2. Entre na execução mais recente de **Build Android APK**.
3. Aguarde o job `build` ficar verde.
4. Em **Artifacts**, baixe `WifiTester-debug-apk`.
5. Extraia o ZIP e instale `app-debug.apk`.

O Android pode pedir autorização para instalar aplicativos vindos do navegador ou do gerenciador de arquivos.

## Permissões

O Android exige permissões de localização e/ou dispositivos Wi‑Fi próximos para liberar resultados de varredura. O app não envia localização para servidores.

## Projeto

- Kotlin
- Android SDK 35
- minSdk 26
- Gradle 8.9 no GitHub Actions
- Java 17

## Uso responsável

Use apenas em redes próprias ou com autorização do responsável pela rede.
