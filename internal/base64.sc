def encode(str: String): String = {
  val enc = java.util.Base64.getEncoder.encode(str.getBytes("UTF-8"))
  new String(enc, "UTF-8")
}

def decode(str: String): String = {
  val enc = java.util.Base64.getDecoder.decode(str.getBytes("UTF-8"))
  new String(enc, "UTF-8")
}
