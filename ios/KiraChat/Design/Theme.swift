import SwiftUI

enum KiraTheme {
    static let green = Color(red: 0.027, green: 0.757, blue: 0.376)
    static let bubbleGreen = Color(red: 0.584, green: 0.925, blue: 0.412)
    static let page = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.063, green: 0.067, blue: 0.078, alpha: 1)
            : UIColor(red: 0.957, green: 0.957, blue: 0.965, alpha: 1)
    })
    static let surface = Color(uiColor: .secondarySystemBackground)
    static let chatSurface = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.102, green: 0.106, blue: 0.118, alpha: 1)
            : UIColor(red: 0.929, green: 0.929, blue: 0.937, alpha: 1)
    })
    static let secondaryText = Color(uiColor: .secondaryLabel)
}

struct KiraPressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.975 : 1)
            .opacity(configuration.isPressed ? 0.82 : 1)
            .animation(
                reduceMotion ? nil : .interactiveSpring(
                    response: 0.22,
                    dampingFraction: 1,
                    blendDuration: 0.05),
                value: configuration.isPressed)
    }
}

extension View {
    func kiraCard(cornerRadius: CGFloat = 18) -> some View {
        background(KiraTheme.surface, in: RoundedRectangle(
            cornerRadius: cornerRadius,
            style: .continuous))
    }
}

