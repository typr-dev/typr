package typr.grpc.parser

/** Errors that can occur during Protobuf schema parsing */
sealed trait GrpcParseError {
  def message: String
}

object GrpcParseError {
  case class DirectoryNotFound(path: String) extends GrpcParseError {
    def message: String = s"Directory not found: $path"
  }

  case class FileReadError(path: String, details: String) extends GrpcParseError {
    def message: String = s"Failed to read file $path: $details"
  }

  case class ProtocNotFound(details: String) extends GrpcParseError {
    def message: String = s"Failed to locate or download protoc: $details"
  }

  case class ProtocFailed(exitCode: Int, stderr: String) extends GrpcParseError {
    def message: String = s"protoc failed with exit code $exitCode: $stderr"
  }

  case class DescriptorParseError(details: String) extends GrpcParseError {
    def message: String = s"Failed to parse protobuf descriptor set: $details"
  }

  case class MultipleErrors(errors: List[String]) extends GrpcParseError {
    def message: String = s"Multiple errors:\n${errors.mkString("\n")}"
  }

  case class UnexpectedError(details: String) extends GrpcParseError {
    def message: String = s"Unexpected error: $details"
  }
}
