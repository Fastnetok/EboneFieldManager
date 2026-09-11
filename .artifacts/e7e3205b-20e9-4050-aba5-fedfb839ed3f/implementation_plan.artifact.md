# Upgrade New Connection Gift Box to Dashboard Style

This plan upgrades the "New Connection" screen to match the dashboard style, including counters, drag-and-drop logic, and functional buttons.

## Proposed Changes

### [Models]
#### [MODIFY] [NewConnection.kt](file:///C:/Users/Dell/AndroidStudioProjects/EboneFieldManager/app/src/main/java/com/fastnet/ebonefieldmanager/NewConnection.kt)
- Add `var displayOrder: Long = 0` to support drag-and-drop saving.

### [Layouts]
#### [MODIFY] [activity_new_connection.xml](file:///C:/Users/Dell/AndroidStudioProjects/EboneFieldManager/app/src/main/res/layout/activity_new_connection.xml)
- Add a proper header with a back button.
- Add a "Counters" row (Pending vs. Installed Today) using the same style as the main dashboard.
- Improve `RecyclerView` padding and margins.

#### [MODIFY] [item_new_connection.xml](file:///C:/Users/Dell/AndroidStudioProjects/EboneFieldManager/app/src/main/res/layout/item_new_connection.xml)
- Add "CALL" and "WHATSAPP" buttons.
- Use a `RelativeLayout` or improved `LinearLayout` for better alignment.
- Add a "Drag Handle" icon hint.

### [Activities]
#### [MODIFY] [NewConnectionActivity.kt](file:///C:/Users/Dell/AndroidStudioProjects/EboneFieldManager/app/src/main/java/com/fastnet/ebonefieldmanager/NewConnectionActivity.kt)
- **Counters**: Listen to both `gift_box` and `completed/YYYY/MM/DD` to update the top cards.
- **Drag & Drop**: Implement `ItemTouchHelper` and `saveDisplayOrder()` logic.
- **Auto-Clear**: The logic already uses date-based paths for completed connections, ensuring only today's successes are counted in the "Installed Today" box.
- **History View**: Add a button or toggle to view connections completed today.

### [Adapters]
#### [MODIFY] [NewConnectionAdapter.kt](file:///C:/Users/Dell/AndroidStudioProjects/EboneFieldManager/app/src/main/java/com/fastnet/ebonefieldmanager/NewConnectionAdapter.kt)
- Wire up Call and WhatsApp button actions.
- Improve UI binding.

## Verification Plan
### Manual Verification
- Open Gift Box. Verify header and counters look like the main dashboard.
- Assign multiple connections to an employee. Verify drag-and-drop works and order is saved (re-open to check).
- Click "Connection Install Successful". Verify item moves from list, "Installed Today" counter increases, and Admin gets notification.
- Test Call and WhatsApp buttons.
- Wait until midnight (or change system clock) and verify "Installed Today" counter resets to 0.
