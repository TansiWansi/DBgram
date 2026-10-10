# Stories and account privacy

The feed starts with stories and the feed filters. The extra desktop tagline,
feed introduction, and inspiration panel have been removed. Suggested creators
and the single theme control remain.

## Stories

Use **Add story** above the feed to upload a photo or video, or share text.
Uploads use the existing resumable upload service. A story can contain one
uploaded photo/video and a caption of up to 2,000 characters, or text alone.

Stories expire 24 hours after publication. The database access function enforces
expiry, including direct post and signed media requests. Expiry removes access;
it does not physically erase the underlying media file.

The tray shows your own stories and accessible stories from accounts you follow,
with unseen stories highlighted. Public stories from other accounts are available
through their profiles. The viewer supports previous/next, hold to pause, explicit
pause/play, video mute, and automatic progression. Authors can see viewers and
delete a story. Replays count once per account. Stories do not increase the
profile's regular post count or appear as ordinary feed posts.

## Public and private accounts

Open **Settings → Privacy & circles → Account privacy** to switch visibility.

- Public: signed-in users can read content within the selected post audience.
- Private: content requires an approved follow relationship. New follow requests
  grant no access until the account owner accepts them.
- Names, usernames, photos, bios, and counts remain discoverable. Private
  accounts' follower/following lists require approval too.
- Existing approved followers remain approved when privacy changes. Pending
  requests still require a decision when an account becomes public.
- Approve or decline requests in Privacy & circles or Notifications. Requesters
  can cancel requests; owners can remove followers from their profile.
- Close friends, custom audiences, communities, and Only me still restrict
  content. Private account rules are an additional requirement. A public
  collaborator cannot expose a private coauthor's post to an unapproved viewer.

The shared PostgreSQL permission function applies to existing content, feed
pages, search, profiles, post details, comments, edit history, and media links.
Removing a follower revokes their access to private content on subsequent
requests, including previously issued signed media URLs. The story viewer also
rechecks permission before each slide and every 15 seconds while open. Content
already downloaded or captured on another device cannot be recalled.

## API

All routes use the existing authenticated `/api` origin; no device IP is embedded.

| Method | Route | Action |
| --- | --- | --- |
| PUT | `/api/users/me/privacy` | Set `{ "isPrivate": true/false }` |
| POST / DELETE | `/api/users/{id}/follow` | Follow/request or cancel/unfollow |
| GET | `/api/users/me/follow-requests` | Incoming requests |
| POST | `/api/users/me/follow-requests/{id}/accept` | Accept a request |
| DELETE | `/api/users/me/follow-requests/{id}` | Decline a request |
| DELETE | `/api/users/me/followers/{id}` | Remove an approved follower |
| POST | `/api/posts` | Publish with `postType: "STORY"` |
| GET | `/api/stories` | Story tray |
| GET | `/api/stories/user/{id}` | Accessible, unexpired stories |
| POST | `/api/stories/{id}/view` | Record one view per account |
| GET | `/api/stories/{id}/viewers` | Author-only viewer list |
| DELETE | `/api/posts/{id}` | Delete an owned story |

## Verification

`make -C frontend build` runs lint, TypeScript, frontend tests, and the web export.
The frontend uses the existing file picker in web browsers, including phone
browsers; this change does not add a native media picker dependency.

The backend's `AccountPrivacyStoryTest` uses a real PostgreSQL database when
`TEST_DATABASE_URL`, `TEST_DATABASE_USER`, and `TEST_DATABASE_PASSWORD` are set.
Use the local database credentials from the existing runtime configuration,
then run `make -C backend test`. It creates a separate randomly named test
schema, rolls back each test, and drops that schema afterwards. It covers
request acceptance/cancellation, concurrent-safe counters, existing content,
collaboration, cursor feeds, expiry, views, and story validation.
