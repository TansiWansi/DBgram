import { useSyncExternalStore } from "react";
import { Platform, useWindowDimensions } from "react-native";

const subscribe = () => () => {};
const clientSnapshot = () => true;
const serverSnapshot = () => false;

export function useViewport() {
  const dimensions = useWindowDimensions();
  const hydrated = useSyncExternalStore(subscribe, clientSnapshot, serverSnapshot);

  // Static HTML and the first browser render must choose the same layout.
  // React then applies the real viewport before subsequent interactions.
  const serverLayout = Platform.OS === "web" && !hydrated;
  return {
    ...dimensions,
    width: serverLayout ? 0 : dimensions.width,
    height: serverLayout ? 0 : dimensions.height,
  };
}
