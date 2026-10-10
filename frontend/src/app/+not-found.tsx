import { Shell } from "../components/Shell";
import { Empty, go } from "../components/ui";
export default function NotFound() {
  return (
    <Shell>
      <Empty
        icon="compass"
        title="A little off the beaten path."
        body="This page doesn’t exist, but your next inspiration is a click away."
        action="Back to your feed"
        onAction={() => go("/")}
      />
    </Shell>
  );
}
