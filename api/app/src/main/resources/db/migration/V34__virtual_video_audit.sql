-- V34 (#841): append-only audit trail of the actions on videos of the library (rename and delete).
--
-- V33 is the head migration this builds on.
--
-- Same column set as virtual_course_audit (V11), with video_id in place of course_id: a video belongs to no
-- course (lessons of any course, or none, may use it) and virtual_course_audit.course_id is mandatory, so these
-- rows cannot go there without making that column optional for everyone. video_id is the library's identifier
-- (the value lessons store in virtual_lessons.video_id), VARCHAR(128) like that column. actions: RENAME_VIDEO
-- (previous_value / new_value hold "title=<title>") and DELETE_VIDEO (previous_value holds
-- "videoId=<id>, title=<title>", new_value is NULL). created_at is the instant the application decided
-- (DATETIME(3), UTC, from the Clock port).
--
-- No foreign keys, as in V11: the library lives outside the database and the trail must outlive the video and the
-- people involved. Indexed by video_id: the trail is read per video.
CREATE TABLE virtual_video_audit (
    id BINARY(16) NOT NULL,
    video_id VARCHAR(128) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    action VARCHAR(50) NOT NULL,
    previous_value TEXT NULL,
    new_value TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_virtual_video_audit_video_id (video_id)
);
