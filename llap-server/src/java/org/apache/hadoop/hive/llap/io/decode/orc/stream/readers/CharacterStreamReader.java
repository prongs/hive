/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.llap.io.decode.orc.stream.readers;

import java.io.IOException;

import org.apache.hadoop.hive.llap.io.api.EncodedColumnBatch;
import org.apache.hadoop.hive.llap.io.decode.orc.stream.StreamUtils;
import org.apache.hadoop.hive.ql.io.orc.CompressionCodec;
import org.apache.hadoop.hive.ql.io.orc.InStream;
import org.apache.hadoop.hive.ql.io.orc.OrcProto;
import org.apache.hadoop.hive.ql.io.orc.PositionProvider;
import org.apache.hadoop.hive.ql.io.orc.RecordReaderImpl;

/**
 *
 */
public class CharacterStreamReader extends RecordReaderImpl.StringTreeReader {
  private boolean isFileCompressed;
  private boolean isDictionaryEncoding;

  private CharacterStreamReader(int columnId, int maxLength, OrcProto.Type charType,
      InStream present,
      InStream data, InStream length, InStream dictionary,
      boolean isFileCompressed,
      OrcProto.ColumnEncoding encoding,
      OrcProto.RowIndexEntry rowIndex) throws IOException {
    super(columnId);
    this.isDictionaryEncoding = dictionary != null;
    if (charType.getKind() == OrcProto.Type.Kind.CHAR) {
      reader = new RecordReaderImpl.CharTreeReader(columnId, maxLength, present, data, length,
          dictionary, encoding);
    } else if (charType.getKind() == OrcProto.Type.Kind.VARCHAR) {
      reader = new RecordReaderImpl.VarcharTreeReader(columnId, maxLength, present, data,
          length, dictionary, encoding);
    } else {
      throw new IOException("Unknown character type " + charType + ". Expected CHAR or VARCHAR.");
    }

    this.isFileCompressed = isFileCompressed;

    // position the readers based on the specified row index
    seek(StreamUtils.getPositionProvider(rowIndex));
  }

  @Override
  public void seek(PositionProvider index) throws IOException {
    if (present != null) {
      if (isFileCompressed) {
        index.getNext();
      }
      present.seek(index);
    }

    if (isDictionaryEncoding) {
      if (isFileCompressed) {
        index.getNext();
      }
      ((RecordReaderImpl.StringDictionaryTreeReader)reader).reader.seek(index);
    } else {
      if (isFileCompressed) {
        index.getNext();
      }
      ((RecordReaderImpl.StringDirectTreeReader)reader).stream.seek(index);

      if (isFileCompressed) {
        index.getNext();
      }
      ((RecordReaderImpl.StringDirectTreeReader)reader).lengths.seek(index);
    }
  }

  public static class StreamReaderBuilder {
    private String fileName;
    private int columnIndex;
    private int maxLength;
    private OrcProto.Type charType;
    private EncodedColumnBatch.StreamBuffer presentStream;
    private EncodedColumnBatch.StreamBuffer dataStream;
    private EncodedColumnBatch.StreamBuffer dictionaryStream;
    private EncodedColumnBatch.StreamBuffer lengthStream;
    private CompressionCodec compressionCodec;
    private int bufferSize;
    private OrcProto.RowIndexEntry rowIndex;
    private OrcProto.ColumnEncoding columnEncoding;

    public StreamReaderBuilder setFileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public StreamReaderBuilder setColumnIndex(int columnIndex) {
      this.columnIndex = columnIndex;
      return this;
    }

    public StreamReaderBuilder setMaxLength(int maxLength) {
      this.maxLength = maxLength;
      return this;
    }

    public StreamReaderBuilder setCharacterType(OrcProto.Type charType) {
      this.charType = charType;
      return this;
    }

    public StreamReaderBuilder setPresentStream(EncodedColumnBatch.StreamBuffer presentStream) {
      this.presentStream = presentStream;
      return this;
    }

    public StreamReaderBuilder setDataStream(EncodedColumnBatch.StreamBuffer dataStream) {
      this.dataStream = dataStream;
      return this;
    }

    public StreamReaderBuilder setLengthStream(EncodedColumnBatch.StreamBuffer lengthStream) {
      this.lengthStream = lengthStream;
      return this;
    }

    public StreamReaderBuilder setDictionaryStream(EncodedColumnBatch.StreamBuffer dictStream) {
      this.dictionaryStream = dictStream;
      return this;
    }

    public StreamReaderBuilder setCompressionCodec(CompressionCodec compressionCodec) {
      this.compressionCodec = compressionCodec;
      return this;
    }

    public StreamReaderBuilder setBufferSize(int bufferSize) {
      this.bufferSize = bufferSize;
      return this;
    }

    public StreamReaderBuilder setRowIndex(OrcProto.RowIndexEntry rowIndex) {
      this.rowIndex = rowIndex;
      return this;
    }

    public StreamReaderBuilder setColumnEncoding(OrcProto.ColumnEncoding encoding) {
      this.columnEncoding = encoding;
      return this;
    }

    public CharacterStreamReader build() throws IOException {
      InStream present = null;
      if (presentStream != null) {
        present = StreamUtils
            .createInStream(OrcProto.Stream.Kind.PRESENT.name(), fileName, null, bufferSize,
                presentStream);
      }

      InStream data = null;
      if (dataStream != null) {
        data = StreamUtils
            .createInStream(OrcProto.Stream.Kind.DATA.name(), fileName, null, bufferSize,
                dataStream);
      }

      InStream length = null;
      if (lengthStream != null) {
        length = StreamUtils
            .createInStream(OrcProto.Stream.Kind.LENGTH.name(), fileName, null, bufferSize,
                lengthStream);
      }

      InStream dictionary = null;
      if (dictionaryStream != null) {
        dictionary = StreamUtils
            .createInStream(OrcProto.Stream.Kind.DICTIONARY_DATA.name(), fileName, null, bufferSize,
                dictionaryStream);
      }
      return new CharacterStreamReader(columnIndex, maxLength, charType, present, data, length,
          dictionary, compressionCodec != null, columnEncoding, rowIndex);
    }
  }

  public static StreamReaderBuilder builder() {
    return new StreamReaderBuilder();
  }

}