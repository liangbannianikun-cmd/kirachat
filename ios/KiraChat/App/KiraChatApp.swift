import SwiftUI

@main
struct KiraChatApp: App {
    @StateObject private var store = AppStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .preferredColorScheme(nil)
                .onChange(of: scenePhase) { phase in
                    if phase == .active { store.syncWhenAppBecomesActive() }
                }
        }
    }
}

