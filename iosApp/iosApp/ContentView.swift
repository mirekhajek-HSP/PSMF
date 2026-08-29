import SwiftUI
import ComposeApp

// The whole UI is Compose Multiplatform. This wrapper exists only to hand
// the Compose view controller to SwiftUI; nothing app-specific belongs
// here, and anything that does is a sign shared code was skipped.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
