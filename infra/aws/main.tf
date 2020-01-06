resource "aws_s3_bucket" "bucket" {
  bucket = "aplus-database-backup"
  acl    = "private"

  tags = {
    Name        = "A+ Database backup"
    Environment = "Production"
  }
}
