import hashlib
import csv
import os

repo_dir = r"C:\Users\ADMIN\.gemini\antigravity\scratch\PRJ301---Group1"
sub_file = os.path.join(repo_dir, "data", "submission.csv")
users_file = os.path.join(repo_dir, "data", "users.csv")

# 1. Update submission.csv with real SHA-256
subs = []
with open(sub_file, 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    fieldnames = reader.fieldnames
    for row in reader:
        content = f"{row['submissionId']}:{row['userId']}:{row['fileUrl']}".encode('utf-8')
        row['fileHash'] = hashlib.sha256(content).hexdigest()
        subs.append(row)

with open(sub_file, 'w', newline='', encoding='utf-8') as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(subs)

print(f"Updated {len(subs)} submissions with real SHA-256 hashes. Sample: {subs[0]['fileHash']}")

# 2. Update users.csv with real SHA-256
users = []
default_hash = hashlib.sha256("password123".encode('utf-8')).hexdigest()
with open(users_file, 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    fieldnames = reader.fieldnames
    for row in reader:
        row['passwordHash'] = default_hash
        users.append(row)

with open(users_file, 'w', newline='', encoding='utf-8') as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(users)

print(f"Updated {len(users)} users with real SHA-256 password hash: {default_hash}")
