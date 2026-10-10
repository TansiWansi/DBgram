import { FeedScreen } from "../screens/FeedScreen";
import { LandingScreen } from "../screens/LandingScreen";
import { useAuth } from "../lib/auth";

export default function Home() {
  const { user } = useAuth();
  return user ? <FeedScreen /> : <LandingScreen />;
}
