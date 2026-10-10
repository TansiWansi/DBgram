import { Image } from "react-native";

export type PhotoImageProps = {
  uri: string;
  onLoad: () => void;
  onError: () => void;
};

export function PhotoImage({ uri, onLoad, onError }: PhotoImageProps) {
  return (
    <Image
      source={{ uri }}
      accessibilityLabel="Post photograph"
      resizeMode="cover"
      onLoad={onLoad}
      onError={onError}
      style={{ width: "100%", height: "100%" }}
    />
  );
}
