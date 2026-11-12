import SwiftUI

@main
struct NosotrosApp: App {
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            ZStack {
                CompanionView()
                if phase != .active {
                    Color(red: 0.055, green: 0.09, blue: 0.12).ignoresSafeArea()
                    Label("Tu espacio privado", systemImage: "lock.fill").foregroundStyle(.white)
                }
            }.preferredColorScheme(.dark)
        }
    }
}

struct CompanionView: View {
    @StateObject private var browser = BrowserModel()
    var body: some View {
        NavigationStack {
            ZStack {
                PanelWeb(model: browser)
                if browser.loading { ProgressView().padding(20).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16)) }
                if let error = browser.error {
                    VStack(spacing: 16) {
                        Image(systemName: "wifi.slash").font(.largeTitle)
                        Text(error).multilineTextAlignment(.center)
                        Button("Volver a intentar") { browser.reload() }.buttonStyle(.borderedProminent)
                    }.padding(25).frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.black)
                }
            }
            .navigationTitle("Nosotros").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) { Button { browser.reload() } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("Recargar panel") }
                ToolbarItem(placement: .navigationBarTrailing) { Link(destination: PanelPolicy.url) { Image(systemName: "safari") }.accessibilityLabel("Abrir en Safari") }
            }
            .safeAreaInset(edge: .bottom) {
                Text("Panel privado · Sin ubicación ni walkie en segundo plano en iOS")
                    .font(.caption2).foregroundStyle(.secondary).padding(8).frame(maxWidth: .infinity).background(.ultraThinMaterial)
            }
        }.tint(Color(red: 0.63, green: 0.89, blue: 0.81))
    }
}
