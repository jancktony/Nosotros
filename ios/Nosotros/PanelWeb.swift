import SwiftUI
import WebKit
import UIKit

@MainActor final class BrowserModel: ObservableObject {
    @Published var loading = true
    @Published var error: String?
    weak var webView: WKWebView?
    func reload() { error = nil; loading = true; webView?.load(URLRequest(url: PanelPolicy.url, cachePolicy: .reloadIgnoringLocalCacheData)) }
}

enum PanelPolicy {
    static let url = URL(string: "https://nosotros-1e43a.web.app/")!
    static func internalURL(_ value: URL) -> Bool { value.scheme == "https" && value.host == url.host }
}

struct PanelWeb: UIViewRepresentable {
    @ObservedObject var model: BrowserModel
    func makeCoordinator() -> Coordinator { Coordinator(model) }
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        // Standard first-party web storage. No injected code, native auth tokens or permissive bridges.
        config.websiteDataStore = .default()
        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = context.coordinator; view.uiDelegate = context.coordinator
        view.isOpaque = false; view.backgroundColor = UIColor(red: 0.055, green: 0.09, blue: 0.12, alpha: 1)
        view.scrollView.backgroundColor = view.backgroundColor
        model.webView = view; view.load(URLRequest(url: PanelPolicy.url))
        return view
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    @MainActor final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        let model: BrowserModel
        init(_ model: BrowserModel) { self.model = model }
        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) { model.loading = true; model.error = nil }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { model.loading = false }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { failed(error) }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { failed(error) }
        private func failed(_ error: Error) {
            if (error as NSError).code == NSURLErrorCancelled { return }
            model.loading = false; model.error = "No se pudo abrir tu panel privado. Revisa la conexión."
        }
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { decisionHandler(.cancel); return }
            if PanelPolicy.internalURL(url) { decisionHandler(.allow); return }
            if action.targetFrame?.isMainFrame == false && url.scheme == "https" && url.host == "nosotros-1e43a.firebaseapp.com" { decisionHandler(.allow); return }
            if action.navigationType == .linkActivated && url.scheme == "https" { UIApplication.shared.open(url) }
            decisionHandler(.cancel)
        }
        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let url = action.request.url, url.scheme == "https", action.navigationType == .linkActivated { UIApplication.shared.open(url) }
            return nil
        }
        private func present(_ alert: UIAlertController, from webView: WKWebView) -> Bool {
            guard var controller = webView.window?.rootViewController else { return false }
            while let next = controller.presentedViewController { controller = next }
            controller.present(alert, animated: true); return true
        }
        func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
            guard frame.securityOrigin.host == PanelPolicy.url.host else { completionHandler(false); return }
            let alert = UIAlertController(title: "Nosotros", message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Cancelar", style: .cancel) { _ in completionHandler(false) })
            alert.addAction(UIAlertAction(title: "Continuar", style: .default) { _ in completionHandler(true) })
            if !present(alert, from: webView) { completionHandler(false) }
        }
        func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
            let alert = UIAlertController(title: "Nosotros", message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Entendido", style: .default) { _ in completionHandler() })
            if !present(alert, from: webView) { completionHandler() }
        }
        func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
            guard frame.securityOrigin.host == PanelPolicy.url.host else { completionHandler(nil); return }
            let alert = UIAlertController(title: "Nosotros", message: prompt, preferredStyle: .alert)
            alert.addTextField { $0.text = defaultText }
            alert.addAction(UIAlertAction(title: "Cancelar", style: .cancel) { _ in completionHandler(nil) })
            alert.addAction(UIAlertAction(title: "Guardar", style: .default) { _ in completionHandler(alert.textFields?.first?.text) })
            if !present(alert, from: webView) { completionHandler(nil) }
        }
    }
}
