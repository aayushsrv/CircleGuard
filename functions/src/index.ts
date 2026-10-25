
import { setGlobalOptions } from "firebase-functions";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";

initializeApp();

const db = getFirestore();

setGlobalOptions({ maxInstances: 5 });

export const notifyGroupOnGeofenceEvent = onDocumentCreated(
  {
    document: "groups/{groupId}/geofenceEvents/{eventId}",
    region: "us-central1",
  },
  async (event) => {
    const snapshot = event.data;

    if (!snapshot) {
      logger.warn("No event document was provided.");
      return;
    }

    const { groupId } = event.params;
    const data = snapshot.data();

    const senderUid = data.uid;
    const transition = data.transition;

    if (
      typeof senderUid !== "string" ||
      !["ENTER", "EXIT"].includes(transition)
    ) {
      logger.warn("Invalid geofence event.", { groupId });
      return;
    }

    // Only notify for events from actual group members.
    const senderRef = db
      .collection("groups")
      .doc(groupId)
      .collection("members")
      .doc(senderUid);

    const senderDoc = await senderRef.get();

    if (!senderDoc.exists) {
      logger.warn("Event sender is not a group member.", {
        groupId,
        senderUid,
      });
      return;
    }

    // Get all group members except the person who triggered the event.
    const membersSnapshot = await db
      .collection("groups")
      .doc(groupId)
      .collection("members")
      .get();

    const recipientUids = membersSnapshot.docs
      .map((member) => member.id)
      .filter((uid) => uid !== senderUid);

    if (recipientUids.length === 0) {
      logger.info("No other group members to notify.", { groupId });
      return;
    }

    // Fetch the device tokens registered by each recipient.
    const tokenResults = await Promise.all(
      recipientUids.map(async (uid) => {
        const devicesSnapshot = await db
          .collection("users")
          .doc(uid)
          .collection("devices")
          .get();

        return devicesSnapshot.docs.map((device) => ({
          uid,
          token: device.id,
        }));
      })
    );

    const devices = tokenResults.flat();

    if (devices.length === 0) {
      logger.info("No recipient device tokens found.", { groupId });
      return;
    }

    const senderName =
      senderDoc.get("displayName") || senderDoc.get("name") || "A member";

    const title =
      transition === "EXIT"
        ? "Safe-zone alert"
        : "Member entered safe zone";

    const body =
      transition === "EXIT"
        ? `${senderName} has left the safe zone.`
        : `${senderName} has entered the safe zone.`;

    // FCM accepts up to 500 tokens per multicast request.
    for (let i = 0; i < devices.length; i += 500) {
      const batch = devices.slice(i, i + 500);

      const response = await getMessaging().sendEachForMulticast({
        tokens: batch.map((device) => device.token),
        notification: { title, body },
        data: {
          groupId,
          senderUid,
          transition,
          eventId: event.params.eventId,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "family_safe_zone_alerts",
          },
        },
      });

      logger.info("Geofence notifications sent.", {
        groupId,
        successCount: response.successCount,
        failureCount: response.failureCount,
      });

      // Remove invalid or expired FCM tokens.
      const cleanupTasks = response.responses.map(
        async (result, index) => {
          if (
            !result.success &&
            (
              result.error?.code ===
                "messaging/registration-token-not-registered" ||
              result.error?.code === "messaging/invalid-registration-token"
            )
          ) {
            const device = batch[index];

            await db
              .collection("users")
              .doc(device.uid)
              .collection("devices")
              .doc(device.token)
              .delete();
          }
        }
      );

      await Promise.all(cleanupTasks);
    }
  }
);
