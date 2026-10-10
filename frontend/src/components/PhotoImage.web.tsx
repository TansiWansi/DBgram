import type { PhotoImageProps } from "./PhotoImage";

export function PhotoImage({ uri, onLoad, onError }: PhotoImageProps) {
  return (
    <img
      src={uri}
      alt="Post photograph"
      loading="lazy"
      decoding="async"
      onLoad={onLoad}
      onError={onError}
      ref={(element) => {
        // A cached image may finish before hydration attaches its load listener.
        if (element?.complete && element.naturalWidth > 0) {
          onLoad();
        }
      }}
      style={{ display: "block", width: "100%", height: "100%", objectFit: "cover" }}
    />
  );
}
