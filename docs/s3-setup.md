# AWS S3 Bucket Setup — africa-shop-dev

## Step 1: Create an AWS Account
If you don't have one, go to https://aws.amazon.com and sign up.

## Step 2: Create the S3 Bucket

1. Go to **S3 Console**: https://s3.console.aws.amazon.com
2. Click **Create bucket**
3. Configure:
   - **Bucket name:** `africa-shop-dev`
   - **AWS Region:** `eu-central-1` (Frankfurt) — or your preferred region
   - **Object Ownership:** ACLs disabled (recommended)
4. **Block Public Access settings:**
   - **Uncheck** "Block all public access" (we need public read for product images)
   - Check the acknowledgment box
5. Leave everything else as default
6. Click **Create bucket**

## Step 3: Set Bucket Policy (Public Read for Images)

1. Go to your bucket → **Permissions** tab
2. Scroll to **Bucket policy** → Click **Edit**
3. Paste this policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadImages",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::africa-shop-dev/products/*"
    }
  ]
}
```

4. Click **Save changes**

This allows anyone to **read** images under the `products/` prefix (which is where our backend uploads them), but not list, delete, or upload.

## Step 4: Configure CORS on the Bucket

1. Still in **Permissions** tab → scroll to **CORS configuration** → Click **Edit**
2. Paste:

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT"],
    "AllowedOrigins": ["http://localhost:3000"],
    "ExposeHeaders": [],
    "MaxAgeSeconds": 3600
  }
]
```

3. Click **Save changes**

Replace `http://localhost:3000` with your actual frontend URL in production. This allows the frontend to upload files directly to S3 via pre-signed URLs.

## Step 5: Create an IAM User for the Backend

1. Go to **IAM Console**: https://console.aws.amazon.com/iam
2. Click **Users** → **Create user**
3. **User name:** `africa-backend-s3`
4. Click **Next**
5. Select **Attach policies directly**
6. Click **Create policy** → switch to **JSON** tab → paste:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::africa-shop-dev/products/*"
    }
  ]
}
```

7. Name the policy: `AfricaShopS3Access` → **Create policy**
8. Go back to the user creation, refresh policies, search for `AfricaShopS3Access`, select it
9. Click **Next** → **Create user**

## Step 6: Generate Access Keys

1. Click on the user `africa-backend-s3`
2. Go to **Security credentials** tab
3. Scroll to **Access keys** → **Create access key**
4. Select **Application running outside AWS**
5. Click **Next** → **Create access key**
6. **Copy both values now** (you won't see the secret key again):
   - Access key ID: `AKIA...`
   - Secret access key: `wJal...`

## Step 7: Configure the Backend

Set these environment variables:

```bash
export AWS_S3_BUCKET="africa-shop-dev"
export AWS_S3_REGION="eu-central-1"
export AWS_ACCESS_KEY="AKIA..."
export AWS_SECRET_KEY="wJal..."
```

Or update `app/src/main/resources/application.yml` directly for local dev.

## Verify

After starting the backend, test the pre-signed URL endpoint:

```bash
# Login first
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@africe.com","password":"your-password"}' | jq -r .accessToken)

# Get pre-signed URL
curl -X POST http://localhost:8080/api/v1/admin/products/images/presign \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"test.jpg","contentType":"image/jpeg"}'
```

You should get back `uploadUrl` and `publicUrl`. Upload a file to the `uploadUrl` with a PUT request, then verify it's accessible at the `publicUrl`.
