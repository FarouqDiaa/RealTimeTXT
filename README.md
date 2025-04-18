# RealTimeTXT

RealTimeTXT is a real-time collaborative plain text editor developed in Java for the CMPS211 Advanced Programming Techniques course at Cairo University.

It enables multiple users to edit the same document simultaneously, with shareable session codes, real-time updates, and cursor tracking. The system uses a custom-built Conflict-free Replicated Data Type (CRDT) to manage concurrent edits efficiently.

---

## ✨ Features

- 🚀 **Real-Time Collaboration** – Edit with others simultaneously.
- 🔒 **Sharable Session Codes** – Invite editors or viewers using unique codes.
- 🧠 **Custom CRDT Engine** – Handles concurrent edits conflict-free.
- 🧑‍💻 **Cursor Tracking** – See the position of all users’ cursors in real time.
- 🔄 **Undo/Redo** – Undo/redo up to 3 personal actions.
- 📁 **Import/Export** – Work with `.txt` files seamlessly.
- 🔐 **Permission Modes** – Viewers can’t edit or access shareable codes.
- 👥 **User Presence Display** – See who’s active in the session.

---

## 📦 Technologies Used

- Java (Core logic)
- JavaFX (User Interface)
- TCP Sockets (Networking)
- Custom Tree-based CRDT (Concurrency Handling)

---

## 📂 Project Structure

```
RealTimeTXT/
├── client/
│   ├── ui/              # User Interface code
│   ├── network/         # Client-side networking
│   ├── logic/           # CRDT and client logic
│   └── MainClient.java  # Client main entry
├── server/
│   ├── ServerMain.java  # Server main entry
│   └── SessionManager.java
├── shared/              # Shared models (User, Document, Operation)
```

---

---

## 👥 Team Members

- Farouq Diaa Eldin
- Mahmoud Aly
- Mostafa Ihab
- Mohamed Maher

---

## 📄 License

This project is for educational use only.
