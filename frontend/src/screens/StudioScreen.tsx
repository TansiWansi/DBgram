import React, { useState } from "react";
import { Linking, Pressable, Text, View } from "react-native";
import { useLocalSearchParams } from "expo-router";
import { Gate, Shell } from "../components/Shell";
import {
  Button,
  ErrorNote,
  Field,
  go,
  Icon,
  ResourceState,
  SectionTitle,
  Tag,
  useUI,
} from "../components/ui";
import { useAuth } from "../lib/auth";
import { errorMessage, idOf, list, request, safeLink } from "../lib/api";
import { useResource } from "../lib/useResource";
export function AnalyticsScreen() {
  return (
    <Shell>
      <Gate>
        <Studio />
      </Gate>
    </Shell>
  );
}
function Studio() {
  const { c, s, compact } = useUI();
  const resource = useResource("/analytics/me");
  const billing = useResource("/billing/status");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const data = resource.data;
  async function billingAction(path: string) {
    setBusy(true);
    try {
      const result = await request(`/billing/${path}`, {
        method: "POST",
        body: {},
      });
      const url = safeLink(
        result.url || result.checkoutUrl || result.portalUrl,
      );
      if (!url)
        throw new Error(
          "No checkout link is available. Please try again later.",
        );
      await Linking.openURL(url);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <SectionTitle
        title="See your ideas grow."
        subtitle="Your creator studio. Real activity, a little perspective."
      />
      <ResourceState resource={resource}>
        {data && (
          <View style={{ gap: 24 }}>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 14 }}>
              {[
                [data.posts, "Posts", "grid"],
                [data.views, "Views", "eye"],
                [data.likes, "Likes", "heart"],
                [data.comments, "Comments", "message-circle"],
                [data.saves, "Saves", "bookmark"],
                [data.followers, "Followers", "users"],
              ].map(([value, label, icon]) => (
                <View
                  key={label}
                  style={[
                    s.card,
                    { flexGrow: 1, flexBasis: compact ? "44%" : "28%", padding: compact ? 16 : 24, gap: 17 },
                  ]}
                >
                  <View style={[s.row, { justifyContent: "space-between", flexWrap: "wrap" }]}>
                    <Text style={{ color: c.muted, fontSize: 12 }}>
                      {label}
                    </Text>
                    <Icon name={icon} size={17} color={c.accentText} />
                  </View>
                  <Text
                    style={{
                      color: c.text,
                      fontSize: 34,
                      fontWeight: "600",
                      letterSpacing: -1,
                    }}
                  >
                    {Number(value || 0).toLocaleString()}
                  </Text>
                </View>
              ))}
            </View>
            <View style={[s.card, { padding: 24, gap: 20 }]}>
              <View style={[s.row, { justifyContent: "space-between", flexWrap: "wrap" }]}>
                <Text
                  style={{ color: c.text, fontWeight: "600", fontSize: 18 }}
                >
                  Your rhythm
                </Text>
                <Tag text="Daily views" />
              </View>
              <View
                style={{
                  height: 140,
                  flexDirection: "row",
                  alignItems: "flex-end",
                  gap: 5,
                  borderBottomWidth: 1,
                  borderColor: c.line,
                }}
              >
                {(data.daily || []).map((day: any) => (
                  <View
                    key={day.date}
                    accessibilityLabel={`${day.date}: ${day.views} views`}
                    style={{
                      flex: 1,
                      backgroundColor: Number(day.views) ? c.accent : c.raised,
                      height: Math.max(
                        3,
                        (Number(day.views) /
                          Math.max(
                            1,
                            ...(data.daily || []).map((d: any) =>
                              Number(d.views),
                            ),
                          )) *
                          135,
                      ),
                      borderTopLeftRadius: 4,
                      borderTopRightRadius: 4,
                    }}
                  />
                ))}
              </View>
              <Text style={s.body}>
                {data.views
                  ? `${Number(data.engagementRate || 0).toFixed(1)}% engagement across your published work.`
                  : "Your chart will come to life as people discover your posts."}
              </Text>
            </View>
            {list(data.topPosts).length > 0 && (
              <View style={{ gap: 15 }}>
                <Text
                  style={{ color: c.text, fontSize: 20, fontWeight: "600" }}
                >
                  Ideas making a mark
                </Text>
                {list(data.topPosts).map((post) => (
                  <Pressable
                    key={post.id}
                    onPress={() => go(`/post?id=${post.id}`)}
                    style={[s.card, { padding: 22, gap: 12 }]}
                  >
                    <Text style={s.label} numberOfLines={2}>
                      {post.textContent ||
                        post.blocks?.[0]?.data?.content ||
                        "Your post"}
                    </Text>
                    <Text style={s.body}>
                      {post.viewCount || 0} views · {post.likeCount || 0} likes
                      · {post.commentCount || 0} comments
                    </Text>
                  </Pressable>
                ))}
              </View>
            )}
            <View style={[s.card, { padding: 24, gap: 17 }]}>
              <View style={s.row}>
                <Icon name="zap" color={c.accentText} />
                <Text
                  style={{ color: c.text, fontSize: 20, fontWeight: "600" }}
                >
                  Creator membership
                </Text>
              </View>
              <ErrorNote message={error} />
              {billing.error ? (
                <ErrorNote message={billing.error} />
              ) : (
                <>
                  <Text style={s.body}>
                    {billing.data?.active
                      ? "Your membership is active. Manage your subscription and payment preferences here."
                      : billing.data?.checkoutAvailable
                        ? "Unlock your creator membership through secure checkout."
                        : "Membership checkout is not available yet. Your core creator tools are ready to use."}
                  </Text>
                  {billing.data?.active && (
                    <Button
                      quiet
                      title="Manage membership"
                      busy={busy}
                      onPress={() => billingAction("portal")}
                    />
                  )}
                  {!billing.data?.active && billing.data?.checkoutAvailable && (
                    <Button
                      title="View membership checkout"
                      busy={busy}
                      onPress={() => billingAction("checkout")}
                    />
                  )}
                </>
              )}
            </View>
          </View>
        )}
      </ResourceState>
    </>
  );
}
export function ReportScreen() {
  return (
    <Shell>
      <Gate>
        <Reports />
      </Gate>
    </Shell>
  );
}
function Reports() {
  const { c, s } = useUI();
  const { postId } = useLocalSearchParams<{ postId?: string }>();
  const { user } = useAuth();
  const resource = useResource("/copyright/claims");
  const capabilities = useResource("/copyright/capabilities");
  const [target, setTarget] = useState(postId || "");
  const [description, setDescription] = useState("");
  const [original, setOriginal] = useState("");
  const [evidence, setEvidence] = useState("");
  const [type, setType] = useState("COPYRIGHT");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const [notes, setNotes] = useState("");
  const claim = useResource(selected ? `/copyright/claims/${selected}` : null);
  async function submit() {
    setError("");
    setSuccess("");
    const id = target.match(
      /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i,
    )?.[0];
    if (!id) {
      setError(
        "Paste the link to the post you want to report, or use Report from its menu.",
      );
      return;
    }
    if (description.trim().length < 20) {
      setError("Please explain the concern in at least 20 characters.");
      return;
    }
    setBusy(true);
    try {
      await request("/copyright/claims", {
        method: "POST",
        body: {
          targetPostId: id,
          description,
          originalWorkUrl: original || null,
          evidenceUrls: evidence
            .split("\n")
            .map((v) => v.trim())
            .filter(Boolean),
          claimType: type,
        },
      });
      setDescription("");
      setSuccess(
        "Your report has been submitted. You can follow its progress below.",
      );
      resource.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function transition(status: string) {
    setError("");
    if (notes.trim().length < 10) {
      setError("Add a short explanation of at least 10 characters.");
      return;
    }
    setBusy(true);
    try {
      await request(`/copyright/claims/${selected}`, {
        method: "PATCH",
        body: { status, notes, restoreContent: status === "RESOLVED" },
      });
      setNotes("");
      claim.reload();
      resource.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const item = claim.data;
  const moderator = capabilities.data?.moderator;
  const statuses = item
    ? [
        ...(["PENDING", "REVIEWING"].includes(item.status) &&
        item.claimantId === idOf(user)
          ? ["WITHDRAWN"]
          : []),
        ...(item.status === "UPHELD" && item.targetUserId === idOf(user)
          ? ["COUNTER_CLAIMED"]
          : []),
        ...(moderator
          ? (
              {
                PENDING: ["REVIEWING"],
                REVIEWING: ["UPHELD", "REJECTED"],
                COUNTER_CLAIMED: ["COUNTER_REVIEWING"],
                COUNTER_REVIEWING: ["RESOLVED"],
              } as any
            )[item.status] || []
          : []),
      ]
    : [];
  return (
    <>
      <SectionTitle
        title="Keep this space thoughtful."
        subtitle="Report a concern, protect your work, and follow the review."
      />
      <View style={[s.card, { padding: 24, gap: 17, marginBottom: 28 }]}>
        <ErrorNote message={error} />
        {success && (
          <Text style={{ color: c.accentText, lineHeight: 22 }}>{success}</Text>
        )}
        <Field
          label="Post link"
          value={target}
          onChangeText={setTarget}
          placeholder="Paste the link to a DBgram post"
        />
        <View style={[s.row, { flexWrap: "wrap" }]}>
          {["COPYRIGHT", "HARASSMENT", "SPAM", "OTHER"].map((value) => (
            <Tag
              key={value}
              text={value.charAt(0) + value.slice(1).toLowerCase()}
              active={type === value}
              onPress={() => setType(value)}
            />
          ))}
        </View>
        <Field
          label="What happened?"
          value={description}
          onChangeText={setDescription}
          multiline
          placeholder="Help the review team understand your concern."
          maxLength={5000}
        />
        {type === "COPYRIGHT" && (
          <Field
            label="Link to your original work"
            value={original}
            onChangeText={setOriginal}
            placeholder="https://…"
          />
        )}
        <Field
          label="Supporting links (one per line)"
          value={evidence}
          onChangeText={setEvidence}
          multiline
          placeholder="Add any context that helps the review"
        />
        <Button
          title="Submit for review"
          icon="shield"
          busy={busy}
          onPress={submit}
        />
      </View>
      <SectionTitle
        title={moderator ? "Review queue" : "Your reports & responses"}
      />
      <ResourceState
        resource={resource}
        empty={!list(resource.data).length}
        title="Nothing to review."
        body="Your reports and any claims involving your work will appear here."
        icon="shield"
      >
        <View style={{ gap: 13 }}>
          {list(resource.data).map((value) => (
            <Pressable
              key={value.id}
              onPress={() => setSelected(value.id)}
              style={[s.card, { padding: 21, gap: 12 }]}
            >
              <View style={[s.row, { justifyContent: "space-between" }]}>
                <Text style={s.label}>
                  {value.claimType || "Copyright"} report
                </Text>
                <Tag
                  text={value.status.replace(/_/g, " ")}
                  active={value.status === "RESOLVED"}
                />
              </View>
              <Text style={s.body} numberOfLines={2}>
                {value.description}
              </Text>
              <Text style={{ color: c.dim, fontSize: 11 }}>
                {new Date(value.createdAt).toLocaleString()}
              </Text>
            </Pressable>
          ))}
        </View>
      </ResourceState>
      {selected && (
        <View style={[s.card, { padding: 24, gap: 18, marginTop: 22 }]}>
          <Text style={s.title}>Review details</Text>
          <ResourceState resource={claim}>
            {item && (
              <>
                <Tag text={item.status} />
                <Text style={s.body}>{item.description}</Text>
                {item.hashMatch != null && (
                  <Text style={s.body}>
                    File hash comparison:{" "}
                    {item.hashMatch ? "Matching files" : "Files do not match"}
                  </Text>
                )}
                {(item.events || []).map((event: any) => (
                  <View
                    key={event.id}
                    style={{
                      borderLeftWidth: 2,
                      borderColor: c.line,
                      paddingLeft: 15,
                      gap: 6,
                    }}
                  >
                    <Text style={s.label}>{event.action}</Text>
                    <Text style={s.body}>{event.notes}</Text>
                    <Text style={{ color: c.dim, fontSize: 11 }}>
                      {new Date(event.createdAt).toLocaleString()}
                    </Text>
                  </View>
                ))}
                {statuses.length > 0 && (
                  <>
                    <Field
                      label="Response / review notes"
                      value={notes}
                      onChangeText={setNotes}
                      multiline
                    />
                    <View style={[s.row, { flexWrap: "wrap" }]}>
                      {statuses.map((status: string) => (
                        <Button
                          key={status}
                          small
                          quiet
                          title={
                            (
                              {
                                WITHDRAWN: "Withdraw report",
                                COUNTER_CLAIMED: "Submit counter-claim",
                                REVIEWING: "Begin review",
                                UPHELD: "Uphold claim",
                                REJECTED: "Reject claim",
                                COUNTER_REVIEWING: "Review response",
                                RESOLVED: "Resolve & restore",
                              } as any
                            )[status]
                          }
                          busy={busy}
                          onPress={() => transition(status)}
                        />
                      ))}
                    </View>
                  </>
                )}
              </>
            )}
          </ResourceState>
        </View>
      )}
    </>
  );
}
export function CommunityEvents({ group }: { group: any }) {
  const { c, s } = useUI();
  const { user } = useAuth();
  const resource = useResource(
    group.isMember ? `/communities/${group.id}/events` : null,
  );
  const [create, setCreate] = useState(false);
  const [fields, setFields] = useState({
    title: "",
    description: "",
    startsAt: "",
    endsAt: "",
    meetLink: "",
    maxAttendees: "",
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const owner = group.ownerId === idOf(user);
  async function save() {
    setError("");
    if (
      ![fields.startsAt, fields.endsAt].every((v) =>
        Number.isFinite(Date.parse(v)),
      )
    ) {
      setError("Use a valid start and end date, like 2026-10-12T18:00.");
      return;
    }
    setBusy(true);
    try {
      await request(`/communities/${group.id}/events`, {
        method: "POST",
        body: {
          ...fields,
          startsAt: new Date(fields.startsAt).toISOString(),
          endsAt: new Date(fields.endsAt).toISOString(),
          meetLink: fields.meetLink || null,
          maxAttendees: fields.maxAttendees
            ? Number(fields.maxAttendees)
            : null,
        },
      });
      setCreate(false);
      resource.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  if (!group.isMember) return null;
  return (
    <View style={{ gap: 20, marginBottom: 28 }}>
      <SectionTitle
        title="Meet around an idea."
        subtitle="Community gatherings, workshops, and little get-togethers."
        aside={
          owner ? (
            <Button
              title="Plan an event"
              quiet
              small
              onPress={() => setCreate(!create)}
            />
          ) : undefined
        }
      />
      <ErrorNote message={error} />
      {create && (
        <View style={[s.card, { padding: 22, gap: 15 }]}>
          {[
            ["title", "Event title"],
            ["description", "About this gathering"],
            ["startsAt", "Starts (local time, YYYY-MM-DDTHH:mm)"],
            ["endsAt", "Ends (local time, YYYY-MM-DDTHH:mm)"],
            ["meetLink", "Google Meet join link (optional)"],
            ["maxAttendees", "Maximum attendees (optional)"],
          ].map(([key, label]) => (
            <Field
              key={key}
              label={label}
              value={(fields as any)[key]}
              onChangeText={(value) =>
                setFields((f) => ({ ...f, [key]: value }))
              }
              multiline={key === "description"}
            />
          ))}
          <Button
            title="Publish event"
            busy={busy}
            disabled={!fields.title.trim()}
            onPress={save}
          />
        </View>
      )}
      <ResourceState
        resource={resource}
        empty={!list(resource.data).length}
        title="Something to look forward to."
        body="Your community’s upcoming events will appear here."
        icon="calendar"
      >
        {list(resource.data).map((event) => (
          <View key={event.id} style={[s.card, { padding: 23, gap: 14 }]}>
            <View style={[s.row, { justifyContent: "space-between" }]}>
              <Icon name="calendar" color={c.accentText} />
              <Tag text={event.status} active={event.status === "LIVE"} />
            </View>
            <Text style={{ color: c.text, fontSize: 20, fontWeight: "600" }}>
              {event.title}
            </Text>
            <Text style={s.body}>{event.description}</Text>
            <Text style={s.body}>
              {new Date(event.startsAt).toLocaleString()} –{" "}
              {new Date(event.endsAt).toLocaleTimeString([], {
                hour: "2-digit",
                minute: "2-digit",
              })}
            </Text>
            <Text style={{ color: c.accentText, fontSize: 12 }}>
              {event.rsvpCount} attending
              {event.maxAttendees ? ` / ${event.maxAttendees} places` : ""}
            </Text>
            <View style={[s.row, { flexWrap: "wrap" }]}>
              {!["CANCELED", "ENDED"].includes(event.status) && (
                <Button
                  title={event.attending ? "Cancel RSVP" : "Count me in"}
                  small
                  quiet={event.attending}
                  onPress={async () => {
                    try {
                      await request(`/events/${event.id}/rsvp`, {
                        method: event.attending ? "DELETE" : "POST",
                      });
                      resource.reload();
                    } catch (e) {
                      setError(errorMessage(e));
                    }
                  }}
                />
              )}
              {event.meetLink &&
                event.attending &&
                !["CANCELED", "ENDED"].includes(event.status) && (
                  <Button
                    title="Join gathering"
                    small
                    icon="video"
                    onPress={() => {
                      const link = safeLink(event.meetLink);
                      if (link) void Linking.openURL(link);
                    }}
                  />
                )}
              {owner && !["CANCELED", "ENDED"].includes(event.status) && (
                <Button
                  title="Cancel event"
                  small
                  quiet
                  onPress={async () => {
                    try {
                      await request(`/events/${event.id}`, {
                        method: "DELETE",
                      });
                      resource.reload();
                    } catch (e) {
                      setError(errorMessage(e));
                    }
                  }}
                />
              )}
            </View>
          </View>
        ))}
      </ResourceState>
    </View>
  );
}


