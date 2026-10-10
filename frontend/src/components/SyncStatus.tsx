import { useEffect, useState } from "react";
import { Platform, Text, View } from "react-native";
import { Button, Icon, useUI } from "./ui";
import { queueStatus, subscribeQueue, syncQueue } from "../lib/offline";
export function SyncStatus() {
  const { c, s } = useUI();
  // A static export cannot know the device's network or pending queue state.
  // Render the same initial markup, then subscribe once the screen is mounted.
  const [status, setStatus] = useState<ReturnType<typeof queueStatus> | null>(null);
  useEffect(() => {
    const update = () => setStatus(queueStatus());
    const stop = subscribeQueue(update);
    update();
    const online = () => {
      update();
      void syncQueue();
    };
    if (Platform.OS === "web") {
      window.addEventListener("online", online);
      window.addEventListener("offline", update);
    }
    const timer = setInterval(() => {
      void syncQueue();
    }, 30000);
    return () => {
      stop();
      clearInterval(timer);
      if (Platform.OS === "web") {
        window.removeEventListener("online", online);
        window.removeEventListener("offline", update);
      }
    };
  }, []);
  if (!status || (status.online && !status.count && !status.issue)) {
    return null;
  }
  return (
    <View
      accessibilityLiveRegion="polite"
      style={[
        s.row,
        {
          paddingHorizontal: 20,
          paddingVertical: 10,
          backgroundColor: c.accentSoft,
          flexWrap: "wrap",
        },
      ]}
    >
      <Icon
        name={status.online ? "refresh-cw" : "wifi-off"}
        size={15}
        color={c.accentText}
      />
      <Text style={{ color: c.text, fontSize: 12, flex: 1 }}>
        {!status.online
          ? "You’re offline. Likes and saves will sync when you reconnect."
          : status.issue || `${status.count} action(s) waiting to sync.`}
      </Text>
      {status.online && status.count > 0 && (
        <Button
          title="Sync now"
          small
          busy={status.syncing}
          onPress={() => {
            void syncQueue();
          }}
        />
      )}
    </View>
  );
}
