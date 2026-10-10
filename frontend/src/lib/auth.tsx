import React, { createContext, useContext, useEffect, useState } from "react";
import { configureQueue } from "./offline";
import {
  request,
  refreshSession,
  rememberSession,
  forgetSession,
  setExpiredHandler,
} from "./api";

type Auth = {
  user: any;
  loading: boolean;
  signIn: (fields: any, register?: boolean) => Promise<void>;
  signOut: () => Promise<void>;
  reloadUser: () => Promise<void>;
};
const Context = createContext<Auth>(null!);
export function AuthProvider({ children }: React.PropsWithChildren) {
  const [user, setUser] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    configureQueue(user?.id || null);
  }, [user?.id]);
  async function reloadUser() {
    setUser(await request("/users/me"));
  }
  useEffect(() => {
    let mounted = true;
    setExpiredHandler(() => setUser(null));
    refreshSession()
      .then(() => request("/users/me"))
      .then((value) => {
        if (mounted) setUser(value);
      })
      .catch(() => {})
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
      setExpiredHandler(() => {});
    };
  }, []);
  async function signIn(fields: any, register = false) {
    const data = await request(register ? "/auth/register" : "/auth/login", {
      method: "POST",
      body: fields,
    });
    await rememberSession(data);
    await reloadUser();
  }
  async function signOut() {
    try { await request("/auth/logout", { method: "POST", body: {} }); }
    finally { await forgetSession(); setUser(null); }
  }
  return (
    <Context.Provider value={{ user, loading, signIn, signOut, reloadUser }}>
      {children}
    </Context.Provider>
  );
}
export const useAuth = () => useContext(Context);

