import SwiftUI

struct CharacterAvatar: View {
    let character: CharacterCard
    var size: CGFloat = 48

    var body: some View {
        Group {
            if let data = character.avatarData,
               let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else if character.isBuiltIn {
                Image("Dounai")
                    .resizable()
                    .scaledToFill()
            } else {
                ZStack {
                    LinearGradient(
                        colors: [.indigo.opacity(0.82), .pink.opacity(0.72)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing)
                    Text(character.initials)
                        .font(.system(size: size * 0.38, weight: .semibold))
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.18, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: size * 0.18, style: .continuous))
    }
}

struct PersonaAvatar: View {
    let data: Data?
    var size: CGFloat = 42

    var body: some View {
        Group {
            if let data, let image = UIImage(data: data) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ZStack {
                    Color(red: 0.35, green: 0.67, blue: 0.88)
                    Image(systemName: "person.fill")
                        .font(.system(size: size * 0.46, weight: .medium))
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.18, style: .continuous))
    }
}

struct GroupAvatar: View {
    let members: [CharacterCard]
    var size: CGFloat = 48

    var body: some View {
        let shown = Array(members.prefix(4))
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.18, style: .continuous)
                .fill(Color(uiColor: .tertiarySystemFill))
            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: 1), GridItem(.flexible(), spacing: 1)],
                spacing: 1) {
                ForEach(shown) { member in
                    CharacterAvatar(character: member, size: (size - 5) / 2)
                }
            }
            .padding(2)
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.18, style: .continuous))
    }
}

struct EmptyState: View {
    let systemImage: String
    let title: LocalizedStringKey
    let message: LocalizedStringKey

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 42, weight: .light))
                .foregroundStyle(.secondary)
            Text(title).font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 280)
        }
        .padding(28)
    }
}

