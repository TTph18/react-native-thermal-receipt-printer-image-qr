import { NativeModules, NativeEventEmitter, Platform } from "react-native";

import * as EPToolkit from "./utils/EPToolkit";
import { processColumnText } from "./utils/print-column";
import { COMMANDS } from "./utils/printer-commands";
import { connectToHost } from "./utils/net-connect";

const RNUSBPrinter = NativeModules.RNUSBPrinter;
const RNBLEPrinter = NativeModules.RNBLEPrinter;
const RNNetPrinter = NativeModules.RNNetPrinter;

export interface PrinterOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
}

export enum PrinterWidth {
  "58mm" = 58,
  "80mm" = 80,
}

export interface PrinterImageOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
  imageWidth?: number;
  imageHeight?: number;
  printerWidthType?: PrinterWidth;
  // only ios
  paddingX?: number;
}

export interface TSPLImageLabelOptions {
  gapMM?: number;
  dotMM?: number;
  printerWidthMM: number;
  printerHeightMM?: number;
  left?: number;
  top?: number;
  invert?: boolean; // If true, invert bitmap colors (for printers that interpret bit=1 as white instead of black)
}

export interface IUSBPrinter {
  device_name: string;
  vendor_id: string;
  product_id: string;
}

export interface IBLEPrinter {
  device_name: string;
  inner_mac_address: string;
}

export interface INetPrinter {
  host: string;
  port: number;
}

export enum ColumnAlignment {
  LEFT,
  CENTER,
  RIGHT,
}

const textTo64Buffer = (text: string, opts: PrinterOptions) => {
  const defaultOptions = {
    beep: false,
    cut: false,
    tailingLine: false,
    encoding: "UTF8",
  };

  const options = {
    ...defaultOptions,
    ...opts,
  };

  const fixAndroid = "\n";
  const buffer = EPToolkit.exchange_text(text + fixAndroid, options);
  return buffer.toString("base64");
};

const billTo64Buffer = (text: string, opts: PrinterOptions) => {
  const defaultOptions = {
    beep: true,
    cut: true,
    encoding: "UTF8",
    tailingLine: true,
  };
  const options = {
    ...defaultOptions,
    ...opts,
  };
  const buffer = EPToolkit.exchange_text(text, options);
  return buffer.toString("base64");
};

const textPreprocessingIOS = (text: string, canCut = true, beep = true) => {
  let options = {
    beep: beep,
    cut: canCut,
  };
  return {
    text: text
      .replace(/<\/?CB>/g, "")
      .replace(/<\/?CM>/g, "")
      .replace(/<\/?CD>/g, "")
      .replace(/<\/?C>/g, "")
      .replace(/<\/?D>/g, "")
      .replace(/<\/?B>/g, "")
      .replace(/<\/?M>/g, ""),
    opts: options,
  };
};

// const imageToBuffer = async (imagePath: string, threshold: number = 60) => {
//   const buffer = await EPToolkit.exchange_image(imagePath, threshold);
//   return buffer.toString("base64");
// };

const USBPrinter = {
  init: (): Promise<void> =>
    new Promise((resolve, reject) =>
      RNUSBPrinter.init(
        () => resolve(),
        (error: Error) => reject(error)
      )
    ),

  getDeviceList: (): Promise<IUSBPrinter[]> =>
    new Promise((resolve, reject) =>
      RNUSBPrinter.getDeviceList(
        (printers: IUSBPrinter[]) => resolve(printers),
        (error: Error) => reject(error)
      )
    ),

  connectPrinter: (vendorId: string, productId: string): Promise<IUSBPrinter> =>
    new Promise((resolve, reject) =>
      RNUSBPrinter.connectPrinter(
        vendorId,
        productId,
        (printer: IUSBPrinter) => resolve(printer),
        (error: Error) => reject(error)
      )
    ),

  closeConn: (): Promise<void> =>
    new Promise((resolve) => {
      RNUSBPrinter.closeConn();
      resolve();
    }),

  printText: (text: string, opts: PrinterOptions = {}): void =>
    RNUSBPrinter.printRawData(textTo64Buffer(text, opts), (error: Error) =>
      console.warn(error)
    ),

  printBill: (text: string, opts: PrinterOptions = {}): void =>
    RNUSBPrinter.printRawData(billTo64Buffer(text, opts), (error: Error) =>
      console.warn(error)
    ),
  /**
   * image url
   * @param imgUrl
   * @param opts
   */
  printImage: function (imgUrl: string, opts: PrinterImageOptions = {}) {
    if (Platform.OS === "ios") {
      RNUSBPrinter.printImageData(imgUrl, opts, (error: Error) =>
        console.warn(error)
      );
    } else {
      RNUSBPrinter.printImageData(
        imgUrl,
        opts?.imageWidth ?? 0,
        opts?.imageHeight ?? 0,
        (error: Error) => console.warn(error)
      );
    }
  },
  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: function (Base64: string, opts: PrinterImageOptions = {}) {
    if (Platform.OS === "ios") {
      RNUSBPrinter.printImageBase64(Base64, opts, (error: Error) =>
        console.warn(error)
      );
    } else {
      RNUSBPrinter.printImageBase64(
        Base64,
        opts?.imageWidth ?? 0,
        opts?.imageHeight ?? 0,
        (error: Error) => console.warn(error)
      );
    }
  },
  /**
   * Print raw data (base64 encoded)
   * For ESC/POS printers: Use printText() or printBill() instead
   * For TSPL printers: Use printTSPL() instead
   * @param text Base64-encoded raw data
   */
  printRaw: (text: string): void => {
    if (Platform.OS === "ios") {
    } else {
      RNUSBPrinter.printRawData(text, (error: Error) => console.warn(error));
    }
  },
  /**
   * Print TSPL (TSC Printer Language) commands for label printers
   * @param base64TSPLCommands Base64-encoded TSPL command string
   * Example TSPL commands:
   *   "SIZE 35 mm, 22 mm\r\nGAP 2 mm, 0 mm\r\nCLS\r\nBITMAP 0,0,280,176,2,<data>\r\nPRINT 1,1\r\n"
   * Convert to base64 before passing to this method.
   */
  printTSPL: (base64TSPLCommands: string): void => {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
    } else {
      RNUSBPrinter.printRawData(base64TSPLCommands, (error: Error) => console.warn(error));
    }
  },

  /**
   * Encode TSPL command string to base64 (generated on Kotlin side)
   * Use this if you're generating TSPL commands in Kotlin and just need to encode them
   * @param tsplCommand TSPL command string
   * @returns Promise<string> Base64-encoded TSPL commands
   */
  encodeTSPLCommand: (tsplCommand: string): Promise<string> => {
    return new Promise((resolve, reject) => {
      if (Platform.OS === "ios") {
        reject(new Error("TSPL printing not supported on iOS"));
        return;
      }
      if (!RNUSBPrinter || typeof RNUSBPrinter.encodeTSPLCommand !== "function") {
        reject(new Error("encodeTSPLCommand is not available. Please rebuild the app after updating the native module."));
        return;
      }
      RNUSBPrinter.encodeTSPLCommand(
        tsplCommand,
        (base64: string) => resolve(base64),
        (error: Error) => reject(error)
      );
    });
  },
  /**
   * Generate TSPL and print directly (all on Kotlin side)
   * Convenience method that generates TSPL command and prints it in one call
   * @param width Label width in mm
   * @param height Label height in mm
   * @param gap Gap between labels in mm
   * @param text Text to print
   * @param x X position
   * @param y Y position
   * @param fontSize Font size (1-8)
   */
  printTSPLTextLabel: (
    width: number,
    height: number,
    gap: number,
    text: string,
    x: number,
    y: number,
    fontSize: number
  ): void => {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
    } else {
      if (!RNUSBPrinter || typeof RNUSBPrinter.printTSPLTextLabel !== "function") {
        console.warn("printTSPLTextLabel is not available. Please rebuild the app after updating the native module.");
        return;
      }
      RNUSBPrinter.printTSPLTextLabel(
        width,
        height,
        gap,
        text,
        x,
        y,
        fontSize,
        (error: Error) => console.warn(error)
      );
    }
  },
  /**
   * Print TSPL label with image, then print directly (all on Kotlin side)
   * @param base64Image Base64-encoded image data
   * @param options Configuration options for TSPL image label printing
   */
  printTSPLImageLabel: (
    base64Image: string | null,
    options: TSPLImageLabelOptions
  ): void => {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
      return;
    }
    
    if (!RNUSBPrinter || typeof RNUSBPrinter.printTSPLImageLabel !== "function") {
      console.warn("printTSPLImageLabel is not available. Please rebuild the app after updating the native module.");
      return;
    }

    if (!base64Image) {
      console.warn("base64Image is required for printTSPLImageLabel");
      return;
    }

    // Call native method with options object
    RNUSBPrinter.printTSPLImageLabel(
      base64Image,
      options,
      (error: Error) => console.warn(error)
    );
  },
  /**
   * Generate TSPL commands for auto feed and cut operations (returns base64 string)
   * No text printing, just feed and cut commands
   * @param opts Configuration options for TSPL auto feed and cut
   *            - cut (boolean, default: false): Cut paper after printing
   *            - tailingLine (boolean, default: false): Feed extra paper before printing
   *            - feedDots (number, default: 50): Number of dots to feed
   *            - eop (boolean, default: false): Use EOP (End Of Print) command instead of PRINT
   * @returns Promise<string> Base64-encoded TSPL commands
   */
  generateAutoFeedAndCut: (opts: {
    cut?: boolean;
    tailingLine?: boolean;
    feedDots?: number;
    eop?: boolean;
  } = {}): Promise<string> => {
    return new Promise((resolve, reject) => {
      if (Platform.OS === "ios") {
        reject(new Error("TSPL printing not supported on iOS"));
        return;
      }
      
      if (!RNUSBPrinter || typeof RNUSBPrinter.generateAutoFeedAndCut !== "function") {
        reject(new Error("generateAutoFeedAndCut is not available. Please rebuild the app after updating the native module."));
        return;
      }

      RNUSBPrinter.generateAutoFeedAndCut(
        opts,
        (base64: string) => resolve(base64),
        (error: Error) => reject(error)
      );
    });
  },
  /**
   * Feed paper (TSPL)
   * @param feedDots Number of dots to feed (default: 50)
   * @param eop Use EOP (End Of Print) command instead of PRINT (default: false)
   */
  feed: (feedDots: number = 50, eop: boolean = false): void => {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
      return;
    }
    
    if (!RNUSBPrinter || typeof RNUSBPrinter.generateAutoFeedAndCut !== "function") {
      console.warn("feed is not available. Please rebuild the app after updating the native module.");
      return;
    }

    RNUSBPrinter.generateAutoFeedAndCut(
      {
        cut: false,
        tailingLine: true,
        feedDots,
        eop
      },
      (base64: string) => {
        RNUSBPrinter.printRawData(base64, (error: Error) => console.warn(error));
      },
      (error: Error) => console.warn(error)
    );
  },
  /**
   * Cut paper (TSPL)
   * @param feedDots Number of dots to feed before cutting (default: 50)
   * @param eop Use EOP (End Of Print) command instead of PRINT (default: false)
   */
  cut: (feedDots: number = 50, eop: boolean = false): void => {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
      return;
    }
    
    if (!RNUSBPrinter || typeof RNUSBPrinter.generateAutoFeedAndCut !== "function") {
      console.warn("cut is not available. Please rebuild the app after updating the native module.");
      return;
    }

    RNUSBPrinter.generateAutoFeedAndCut(
      {
        cut: true,
        tailingLine: false,
        feedDots,
        eop
      },
      (base64: string) => {
        RNUSBPrinter.printRawData(base64, (error: Error) => console.warn(error));
      },
      (error: Error) => console.warn(error)
    );
  },
  /**
   * Feed and cut paper (TSPL)
   * @param feedDots Number of dots to feed (default: 50)
   * @param eop Use EOP (End Of Print) command instead of PRINT (default: false)
   */
  feedAndCut: (feedDots: number = 50, eop: boolean = false): void => {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
      return;
    }
    
    if (!RNUSBPrinter || typeof RNUSBPrinter.generateAutoFeedAndCut !== "function") {
      console.warn("feedAndCut is not available. Please rebuild the app after updating the native module.");
      return;
    }

    RNUSBPrinter.generateAutoFeedAndCut(
      {
        cut: true,
        tailingLine: true,
        feedDots,
        eop
      },
      (base64: string) => {
        RNUSBPrinter.printRawData(base64, (error: Error) => console.warn(error));
      },
      (error: Error) => console.warn(error)
    );
  },
  /**
   * `columnWidth`
   * 80mm => 46 character
   * 58mm => 30 character
   */
  printColumnsText: (
    texts: string[],
    columnWidth: number[],
    columnAlignment: ColumnAlignment[],
    columnStyle: string[],
    opts: PrinterOptions = {}
  ): void => {
    const result = processColumnText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle
    );
    RNUSBPrinter.printRawData(textTo64Buffer(result, opts), (error: Error) =>
      console.warn(error)
    );
  },
};

const BLEPrinter = {
  init: (): Promise<void> =>
    new Promise((resolve, reject) =>
      RNBLEPrinter.init(
        () => resolve(),
        (error: Error) => reject(error)
      )
    ),

  getDeviceList: (): Promise<IBLEPrinter[]> =>
    new Promise((resolve, reject) =>
      RNBLEPrinter.getDeviceList(
        (printers: IBLEPrinter[]) => resolve(printers),
        (error: Error) => reject(error)
      )
    ),

  connectPrinter: (inner_mac_address: string): Promise<IBLEPrinter> =>
    new Promise((resolve, reject) =>
      RNBLEPrinter.connectPrinter(
        inner_mac_address,
        (printer: IBLEPrinter) => resolve(printer),
        (error: Error) => reject(error)
      )
    ),

  closeConn: (): Promise<void> =>
    new Promise((resolve) => {
      RNBLEPrinter.closeConn();
      resolve();
    }),

  printText: (text: string, opts: PrinterOptions = {}): void => {
    if (Platform.OS === "ios") {
      const processedText = textPreprocessingIOS(text, false, false);
      RNBLEPrinter.printRawData(
        processedText.text,
        processedText.opts,
        (error: Error) => console.warn(error)
      );
    } else {
      RNBLEPrinter.printRawData(textTo64Buffer(text, opts), (error: Error) =>
        console.warn(error)
      );
    }
  },

  printBill: (text: string, opts: PrinterOptions = {}): void => {
    if (Platform.OS === "ios") {
      const processedText = textPreprocessingIOS(
        text,
        opts?.cut ?? true,
        opts.beep ?? true
      );
      RNBLEPrinter.printRawData(
        processedText.text,
        processedText.opts,
        (error: Error) => console.warn(error)
      );
    } else {
      RNBLEPrinter.printRawData(billTo64Buffer(text, opts), (error: Error) =>
        console.warn(error)
      );
    }
  },
  /**
   * image url
   * @param imgUrl
   * @param opts
   */
  printImage: function (imgUrl: string, opts: PrinterImageOptions = {}) {
    if (Platform.OS === "ios") {
      /**
       * just development
       */
      RNBLEPrinter.printImageData(imgUrl, opts, (error: Error) =>
        console.warn(error)
      );
    } else {
      RNBLEPrinter.printImageData(
        imgUrl,
        opts?.imageWidth ?? 0,
        opts?.imageHeight ?? 0,
        (error: Error) => console.warn(error)
      );
    }
  },
  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: function (Base64: string, opts: PrinterImageOptions = {}) {
    if (Platform.OS === "ios") {
      /**
       * just development
       */
      RNBLEPrinter.printImageBase64(Base64, opts, (error: Error) =>
        console.warn(error)
      );
    } else {
      /**
       * just development
       */
      RNBLEPrinter.printImageBase64(
        Base64,
        opts?.imageWidth ?? 0,
        opts?.imageHeight ?? 0,
        (error: Error) => console.warn(error)
      );
    }
  },
  /**
   * android print with encoder
   * @param text
   */
  printRaw: (text: string): void => {
    if (Platform.OS === "ios") {
      var processedText = textPreprocessingIOS(text, false, false);

      RNBLEPrinter.printRawData(
        processedText.text,
        processedText.opts,
        function (error: Error) {
          return console.warn(error);
        }
      );
    } else {
      RNBLEPrinter.printRawData(text, (error: Error) => console.warn(error));
    }
  },
  /**
   * `columnWidth`
   * 80mm => 46 character
   * 58mm => 30 character
   */
  printColumnsText: (
    texts: string[],
    columnWidth: number[],
    columnAlignment: ColumnAlignment[],
    columnStyle: string[],
    opts: PrinterOptions = {}
  ): void => {
    const result = processColumnText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle
    );
    if (Platform.OS === "ios") {
      const processedText = textPreprocessingIOS(result, false, false);
      RNBLEPrinter.printRawData(
        processedText.text,
        processedText.opts,
        (error: Error) => console.warn(error)
      );
    } else {
      RNBLEPrinter.printRawData(textTo64Buffer(result, opts), (error: Error) =>
        console.warn(error)
      );
    }
  },
};

const NetPrinter = {
  init: (): Promise<void> =>
    new Promise((resolve, reject) =>
      RNNetPrinter.init(
        () => resolve(),
        (error: Error) => reject(error)
      )
    ),

  getDeviceList: (): Promise<INetPrinter[]> =>
    new Promise((resolve, reject) =>
      RNNetPrinter.getDeviceList(
        (printers: INetPrinter[]) => resolve(printers),
        (error: Error) => reject(error)
      )
    ),

  connectPrinter: (
    host: string,
    port: number,
    timeout?: number
  ): Promise<INetPrinter> =>
    new Promise(async (resolve, reject) => {
      try {
        await connectToHost(host, timeout);
        RNNetPrinter.connectPrinter(
          host,
          port,
          (printer: INetPrinter) => resolve(printer),
          (error: Error) => reject(error)
        );
      } catch (error) {
        reject(error?.message || `Connect to ${host} fail`);
      }
    }),

  closeConn: (): Promise<void> =>
    new Promise((resolve) => {
      RNNetPrinter.closeConn();
      resolve();
    }),

  printText: (text: string, opts = {}): void => {
    if (Platform.OS === "ios") {
      const processedText = textPreprocessingIOS(text, false, false);
      RNNetPrinter.printRawData(
        processedText.text,
        processedText.opts,
        (error: Error) => console.warn(error)
      );
    } else {
      RNNetPrinter.printRawData(textTo64Buffer(text, opts), (error: Error) =>
        console.warn(error)
      );
    }
  },

  printBill: (text: string, opts: PrinterOptions = {}): void => {
    if (Platform.OS === "ios") {
      const processedText = textPreprocessingIOS(
        text,
        opts?.cut ?? true,
        opts.beep ?? true
      );
      RNNetPrinter.printRawData(
        processedText.text,
        processedText.opts,
        (error: Error) => console.warn(error)
      );
    } else {
      RNNetPrinter.printRawData(billTo64Buffer(text, opts), (error: Error) =>
        console.warn(error)
      );
    }
  },
  /**
   * image url
   * @param imgUrl
   * @param opts
   */
  printImage: function (imgUrl: string, opts: PrinterImageOptions = {}) {
    if (Platform.OS === "ios") {
      RNNetPrinter.printImageData(imgUrl, opts, (error: Error) =>
        console.warn(error)
      );
    } else {
      RNNetPrinter.printImageData(
        imgUrl,
        opts?.imageWidth ?? 0,
        opts?.imageHeight ?? 0,
        (error: Error) => console.warn(error)
      );
    }
  },
  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: function (Base64: string, opts: PrinterImageOptions = {}) {
    if (Platform.OS === "ios") {
      RNNetPrinter.printImageBase64(Base64, opts, (error: Error) =>
        console.warn(error)
      );
    } else {
      RNNetPrinter.printImageBase64(
        Base64,
        opts?.imageWidth ?? 0,
        opts?.imageHeight ?? 0,
        (error: Error) => console.warn(error)
      );
    }
  },

  /**
   * Android print with encoder
   * @param text
   */
  printRaw: (text: string): void => {
    if (Platform.OS === "ios") {
    } else {
      RNNetPrinter.printRawData(text, (error: Error) => console.warn(error));
    }
  },

  /**
   * `columnWidth`
   * 80mm => 46 character
   * 58mm => 30 character
   */
  printColumnsText: (
    texts: string[],
    columnWidth: number[],
    columnAlignment: ColumnAlignment[],
    columnStyle: string[] = [],
    opts: PrinterOptions = {}
  ): void => {
    const result = processColumnText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle
    );
    if (Platform.OS === "ios") {
      const processedText = textPreprocessingIOS(result, false, false);
      RNNetPrinter.printRawData(
        processedText.text,
        processedText.opts,
        (error: Error) => console.warn(error)
      );
    } else {
      RNNetPrinter.printRawData(textTo64Buffer(result, opts), (error: Error) =>
        console.warn(error)
      );
    }
  },
};

const NetPrinterEventEmitter =
  Platform.OS === "ios"
    ? new NativeEventEmitter(RNNetPrinter)
    : new NativeEventEmitter();

export { COMMANDS, NetPrinter, BLEPrinter, USBPrinter, NetPrinterEventEmitter };

export enum RN_THERMAL_RECEIPT_PRINTER_EVENTS {
  EVENT_NET_PRINTER_SCANNED_SUCCESS = "scannerResolved",
  EVENT_NET_PRINTER_SCANNING = "scannerRunning",
  EVENT_NET_PRINTER_SCANNED_ERROR = "registerError",
}
