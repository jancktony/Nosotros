# Nosotros para iPhone · compañero del panel privado

Cliente SwiftUI/WebKit para iOS 16 o posterior. Abre exclusivamente el panel HTTPS de Nosotros y conserva sus cuentas y reglas de Firebase; no introduce accesos anónimos, credenciales de servidor ni puentes JavaScript privilegiados. Permite gestionar el panel, ver sus teléfonos Android y recuerdos y emitir las órdenes autorizadas desde la web. Incluye confirmaciones, observaciones, recuperación ante errores y apertura explícita de enlaces externos en Safari.

**No es una conversión del APK ni una versión con todas las funciones Android**: no comparte ubicación en segundo plano desde el iPhone, no identifica otras apps de iOS, no ofrece walkie en segundo plano y no recibe las órdenes Android para hacerlo sonar. Esas funciones requieren un desarrollo nativo iOS adicional y, en su caso, servicios y permisos de Apple. La interfaz lo indica. El panel web también se puede añadir a la pantalla de inicio desde Safari sin instalar un IPA.

GitHub Actions compila con Xcode en macOS y genera `Nosotros-iOS-1.11.0-sin-firma.ipa`. El archivo contiene un binario real para arm64, **sin certificado ni perfil de aprovisionamiento**; no se instala directamente. Puede necesitar que una herramienta lo firme con una identidad válida. No se puede garantizar que Scarlet lo firme o que funcione indefinidamente: certificados y perfiles pueden caducar o revocarse. No se incluyen certificados de terceros ni procedimientos para eludir los controles de Apple. No se ha comprado ninguna cuenta ni servicio.

Fuentes: https://developer.apple.com/help/account/provisioning-profiles/create-a-development-provisioning-profile/ ; https://support.apple.com/en-ie/118254
