import { Pressable, Text, View } from "react-native";
import { go, Icon, useUI } from "./ui";

export function Brand({ compact = false }: { compact?: boolean }) {
  const { c, s } = useUI();
  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel="DBgram home"
      onPress={() => go("/")}
      style={[s.row, { minHeight: 44, gap: 10 }]}
    >
      <View style={{ width: 28, height: 28, justifyContent: "center" }}>
        <Icon name="aperture" size={28} color={c.accentText} />
      </View>
      {!compact && (
        <Text style={{ color: c.text, fontSize: 27, fontWeight: "600", letterSpacing: -1.3 }}>
          dbgram<Text style={{ color: c.accentText }}>.</Text>
        </Text>
      )}
    </Pressable>
  );
}
