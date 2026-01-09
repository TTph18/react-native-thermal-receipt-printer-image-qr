var __assign =
  (this && this.__assign) ||
  function () {
    __assign =
      Object.assign ||
      function (t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
          s = arguments[i];
          for (var p in s)
            if (Object.prototype.hasOwnProperty.call(s, p)) t[p] = s[p];
        }
        return t;
      };
    return __assign.apply(this, arguments);
  };
var __awaiter =
  (this && this.__awaiter) ||
  function (thisArg, _arguments, P, generator) {
    function adopt(value) {
      return value instanceof P
        ? value
        : new P(function (resolve) {
            resolve(value);
          });
    }
    return new (P || (P = Promise))(function (resolve, reject) {
      function fulfilled(value) {
        try {
          step(generator.next(value));
        } catch (e) {
          reject(e);
        }
      }
      function rejected(value) {
        try {
          step(generator["throw"](value));
        } catch (e) {
          reject(e);
        }
      }
      function step(result) {
        result.done
          ? resolve(result.value)
          : adopt(result.value).then(fulfilled, rejected);
      }
      step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
  };
var __generator =
  (this && this.__generator) ||
  function (thisArg, body) {
    var _ = {
        label: 0,
        sent: function () {
          if (t[0] & 1) throw t[1];
          return t[1];
        },
        trys: [],
        ops: [],
      },
      f,
      y,
      t,
      g;
    return (
      (g = { next: verb(0), throw: verb(1), return: verb(2) }),
      typeof Symbol === "function" &&
        (g[Symbol.iterator] = function () {
          return this;
        }),
      g
    );
    function verb(n) {
      return function (v) {
        return step([n, v]);
      };
    }
    function step(op) {
      if (f) throw new TypeError("Generator is already executing.");
      while ((g && ((g = 0), op[0] && (_ = 0)), _))
        try {
          if (
            ((f = 1),
            y &&
              (t =
                op[0] & 2
                  ? y["return"]
                  : op[0]
                  ? y["throw"] || ((t = y["return"]) && t.call(y), 0)
                  : y.next) &&
              !(t = t.call(y, op[1])).done)
          )
            return t;
          if (((y = 0), t)) op = [op[0] & 2, t.value];
          switch (op[0]) {
            case 0:
            case 1:
              t = op;
              break;
            case 4:
              _.label++;
              return { value: op[1], done: false };
            case 5:
              _.label++;
              y = op[1];
              op = [0];
              continue;
            case 7:
              op = _.ops.pop();
              _.trys.pop();
              continue;
            default:
              if (
                !((t = _.trys), (t = t.length > 0 && t[t.length - 1])) &&
                (op[0] === 6 || op[0] === 2)
              ) {
                _ = 0;
                continue;
              }
              if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) {
                _.label = op[1];
                break;
              }
              if (op[0] === 6 && _.label < t[1]) {
                _.label = t[1];
                t = op;
                break;
              }
              if (t && _.label < t[2]) {
                _.label = t[2];
                _.ops.push(op);
                break;
              }
              if (t[2]) _.ops.pop();
              _.trys.pop();
              continue;
          }
          op = body.call(thisArg, _);
        } catch (e) {
          op = [6, e];
          y = 0;
        } finally {
          f = t = 0;
        }
      if (op[0] & 5) throw op[1];
      return { value: op[0] ? op[1] : void 0, done: true };
    }
  };
import { NativeModules, NativeEventEmitter, Platform } from "react-native";
import * as EPToolkit from "./utils/EPToolkit";
import { processColumnText } from "./utils/print-column";
import { COMMANDS } from "./utils/printer-commands";
import { connectToHost } from "./utils/net-connect";
var RNUSBPrinter = NativeModules.RNUSBPrinter;
var RNBLEPrinter = NativeModules.RNBLEPrinter;
var RNNetPrinter = NativeModules.RNNetPrinter;
export var PrinterWidth;
(function (PrinterWidth) {
  PrinterWidth[(PrinterWidth["58mm"] = 58)] = "58mm";
  PrinterWidth[(PrinterWidth["80mm"] = 80)] = "80mm";
})(PrinterWidth || (PrinterWidth = {}));
export var ColumnAlignment;
(function (ColumnAlignment) {
  ColumnAlignment[(ColumnAlignment["LEFT"] = 0)] = "LEFT";
  ColumnAlignment[(ColumnAlignment["CENTER"] = 1)] = "CENTER";
  ColumnAlignment[(ColumnAlignment["RIGHT"] = 2)] = "RIGHT";
})(ColumnAlignment || (ColumnAlignment = {}));
var textTo64Buffer = function (text, opts) {
  var defaultOptions = {
    beep: false,
    cut: false,
    tailingLine: false,
    encoding: "UTF8",
  };
  var options = __assign(__assign({}, defaultOptions), opts);
  var fixAndroid = "\n";
  var buffer = EPToolkit.exchange_text(text + fixAndroid, options);
  return buffer.toString("base64");
};
var billTo64Buffer = function (text, opts) {
  var defaultOptions = {
    beep: true,
    cut: true,
    encoding: "UTF8",
    tailingLine: true,
  };
  var options = __assign(__assign({}, defaultOptions), opts);
  var buffer = EPToolkit.exchange_text(text, options);
  return buffer.toString("base64");
};
var textPreprocessingIOS = function (text, canCut, beep) {
  if (canCut === void 0) {
    canCut = true;
  }
  if (beep === void 0) {
    beep = true;
  }
  var options = {
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
var USBPrinter = {
  init: function () {
    return new Promise(function (resolve, reject) {
      return RNUSBPrinter.init(
        function () {
          return resolve();
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  getDeviceList: function () {
    return new Promise(function (resolve, reject) {
      return RNUSBPrinter.getDeviceList(
        function (printers) {
          return resolve(printers);
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  connectPrinter: function (vendorId, productId) {
    return new Promise(function (resolve, reject) {
      return RNUSBPrinter.connectPrinter(
        vendorId,
        productId,
        function (printer) {
          return resolve(printer);
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  closeConn: function () {
    return new Promise(function (resolve) {
      RNUSBPrinter.closeConn();
      resolve();
    });
  },
  printText: function (text, opts) {
    if (opts === void 0) {
      opts = {};
    }
    return RNUSBPrinter.printRawData(
      textTo64Buffer(text, opts),
      function (error) {
        return console.warn(error);
      }
    );
  },
  printBill: function (text, opts) {
    if (opts === void 0) {
      opts = {};
    }
    return RNUSBPrinter.printRawData(
      billTo64Buffer(text, opts),
      function (error) {
        return console.warn(error);
      }
    );
  },
  /**
   * image url
   * @param imgUrl
   * @param opts
   */
  printImage: function (imgUrl, opts) {
    var _a, _b;
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      RNUSBPrinter.printImageData(imgUrl, opts, function (error) {
        return console.warn(error);
      });
    } else {
      RNUSBPrinter.printImageData(
        imgUrl,
        (_a = opts === null || opts === void 0 ? void 0 : opts.imageWidth) !==
          null && _a !== void 0
          ? _a
          : 0,
        (_b = opts === null || opts === void 0 ? void 0 : opts.imageHeight) !==
          null && _b !== void 0
          ? _b
          : 0,
        function (error) {
          return console.warn(error);
        }
      );
    }
  },
  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: function (Base64, opts) {
    var _a, _b, _c, _d;
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      RNUSBPrinter.printImageBase64(Base64, opts, function (error) {
        return console.warn(error);
      });
    } else {
      // USB printer supports x, y positioning
      RNUSBPrinter.printImageBase64(
        Base64,
        (_a = opts === null || opts === void 0 ? void 0 : opts.imageWidth) !==
          null && _a !== void 0
          ? _a
          : 0,
        (_b = opts === null || opts === void 0 ? void 0 : opts.imageHeight) !==
          null && _b !== void 0
          ? _b
          : 0,
        (_c = opts === null || opts === void 0 ? void 0 : opts.x) !==
          null && _c !== void 0
          ? _c
          : -1,
        (_d = opts === null || opts === void 0 ? void 0 : opts.y) !==
          null && _d !== void 0
          ? _d
          : 0,
        function (error) {
          return console.warn(error);
        }
      );
    }
  },
  /**
   * Print raw data (base64 encoded)
   * For ESC/POS printers: Use printText() or printBill() instead
   * For TSPL printers: Use printTSPL() instead
   * @param text Base64-encoded raw data
   */
  printRaw: function (text) {
    if (Platform.OS === "ios") {
    } else {
      RNUSBPrinter.printRawData(text, function (error) {
        return console.warn(error);
      });
    }
  },
  /**
   * Print TSPL (TSC Printer Language) commands for label printers
   * @param base64TSPLCommands Base64-encoded TSPL command string
   * Example TSPL commands:
   *   "SIZE 35 mm, 22 mm\r\nGAP 2 mm, 0 mm\r\nCLS\r\nBITMAP 0,0,280,176,2,<data>\r\nPRINT 1,1\r\n"
   * Convert to base64 before passing to this method.
   */
  printTSPL: function (base64TSPLCommands) {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
    } else {
      RNUSBPrinter.printRawData(base64TSPLCommands, function (error) {
        return console.warn(error);
      });
    }
  },

  /**
   * Encode TSPL command string to base64 (generated on Kotlin side)
   * Use this if you're generating TSPL commands in Kotlin and just need to encode them
   * @param tsplCommand TSPL command string
   * @returns Promise<string> Base64-encoded TSPL commands
   */
  encodeTSPLCommand: function (tsplCommand) {
    return new Promise(function (resolve, reject) {
      if (Platform.OS === "ios") {
        reject(new Error("TSPL printing not supported on iOS"));
        return;
      }
      if (
        !RNUSBPrinter ||
        typeof RNUSBPrinter.encodeTSPLCommand !== "function"
      ) {
        reject(
          new Error(
            "encodeTSPLCommand is not available. Please rebuild the app after updating the native module."
          )
        );
        return;
      }
      RNUSBPrinter.encodeTSPLCommand(
        tsplCommand,
        function (base64) {
          return resolve(base64);
        },
        function (error) {
          return reject(error);
        }
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
  printTSPLTextLabel: function (width, height, gap, text, x, y, fontSize) {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
    } else {
      if (
        !RNUSBPrinter ||
        typeof RNUSBPrinter.printTSPLTextLabel !== "function"
      ) {
        console.warn(
          "printTSPLTextLabel is not available. Please rebuild the app after updating the native module."
        );
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
        function (error) {
          return console.warn(error);
        }
      );
    }
  },
  /**
   * Print TSPL label with image, then print directly
   * @param base64Image Base64-encoded image data (can be null/empty)
   * @param gapMM Gap between labels in mm
   * @param dotMM Dot per mm
   * @param printerWidthMM Printer width in mm
   * @param printerHeightMM Printer height in mm
   * @param left Left position
   * @param top Top position
   */
  printTSPLImageLabel: function (
    base64Image,
    options
  ) {
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
    } else {
      if (
        !RNUSBPrinter ||
        typeof RNUSBPrinter.printTSPLImageLabel !== "function"
      ) {
        console.warn(
          "printTSPLImageLabel is not available. Please rebuild the app after updating the native module."
        );
        return;
      }
      RNUSBPrinter.printTSPLImageLabel(
        base64Image || "",
        options,
        function (error) {
          return console.warn(error);
        }
      );
    }
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
  generateAutoFeedAndCut: function (opts) {
    if (opts === void 0) {
      opts = {};
    }
    return new Promise(function (resolve, reject) {
      if (Platform.OS === "ios") {
        reject(new Error("TSPL printing not supported on iOS"));
        return;
      }
      if (
        !RNUSBPrinter ||
        typeof RNUSBPrinter.generateAutoFeedAndCut !== "function"
      ) {
        reject(
          new Error(
            "generateAutoFeedAndCut is not available. Please rebuild the app after updating the native module."
          )
        );
        return;
      }
      RNUSBPrinter.generateAutoFeedAndCut(
        opts,
        function (base64) {
          return resolve(base64);
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  /**
   * Feed paper (TSPL)
   * @param feedDots Number of dots to feed (default: 50)
   * @param eop Use EOP (End Of Print) command instead of PRINT (default: false)
   */
  feed: function (feedDots, eop) {
    if (feedDots === void 0) {
      feedDots = 50;
    }
    if (eop === void 0) {
      eop = false;
    }
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
      return;
    }
    if (
      !RNUSBPrinter ||
      typeof RNUSBPrinter.generateAutoFeedAndCut !== "function"
    ) {
      console.warn(
        "feed is not available. Please rebuild the app after updating the native module."
      );
      return;
    }
    RNUSBPrinter.generateAutoFeedAndCut(
      {
        cut: false,
        tailingLine: true,
        feedDots: feedDots,
        eop: eop,
      },
      function (base64) {
        RNUSBPrinter.printRawData(base64, function (error) {
          return console.warn(error);
        });
      },
      function (error) {
        return console.warn(error);
      }
    );
  },
  /**
   * Cut paper (TSPL)
   * @param feedDots Number of dots to feed before cutting (default: 50)
   * @param eop Use EOP (End Of Print) command instead of PRINT (default: false)
   */
  cut: function (feedDots, eop) {
    if (feedDots === void 0) {
      feedDots = 50;
    }
    if (eop === void 0) {
      eop = false;
    }
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
      return;
    }
    if (
      !RNUSBPrinter ||
      typeof RNUSBPrinter.generateAutoFeedAndCut !== "function"
    ) {
      console.warn(
        "cut is not available. Please rebuild the app after updating the native module."
      );
      return;
    }
    RNUSBPrinter.generateAutoFeedAndCut(
      {
        cut: true,
        tailingLine: false,
        feedDots: feedDots,
        eop: eop,
      },
      function (base64) {
        RNUSBPrinter.printRawData(base64, function (error) {
          return console.warn(error);
        });
      },
      function (error) {
        return console.warn(error);
      }
    );
  },
  /**
   * Feed and cut paper (TSPL)
   * @param feedDots Number of dots to feed (default: 50)
   * @param eop Use EOP (End Of Print) command instead of PRINT (default: false)
   */
  feedAndCut: function (feedDots, eop) {
    if (feedDots === void 0) {
      feedDots = 50;
    }
    if (eop === void 0) {
      eop = false;
    }
    if (Platform.OS === "ios") {
      console.warn("TSPL printing not supported on iOS");
      return;
    }
    if (
      !RNUSBPrinter ||
      typeof RNUSBPrinter.generateAutoFeedAndCut !== "function"
    ) {
      console.warn(
        "feedAndCut is not available. Please rebuild the app after updating the native module."
      );
      return;
    }
    RNUSBPrinter.generateAutoFeedAndCut(
      {
        cut: true,
        tailingLine: true,
        feedDots: feedDots,
        eop: eop,
      },
      function (base64) {
        RNUSBPrinter.printRawData(base64, function (error) {
          return console.warn(error);
        });
      },
      function (error) {
        return console.warn(error);
      }
    );
  },
  /**
   * `columnWidth`
   * 80mm => 46 character
   * 58mm => 30 character
   */
  printColumnsText: function (
    texts,
    columnWidth,
    columnAlignment,
    columnStyle,
    opts
  ) {
    if (opts === void 0) {
      opts = {};
    }
    var result = processColumnText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle
    );
    RNUSBPrinter.printRawData(textTo64Buffer(result, opts), function (error) {
      return console.warn(error);
    });
  },
};
var BLEPrinter = {
  init: function () {
    return new Promise(function (resolve, reject) {
      return RNBLEPrinter.init(
        function () {
          return resolve();
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  getDeviceList: function () {
    return new Promise(function (resolve, reject) {
      return RNBLEPrinter.getDeviceList(
        function (printers) {
          return resolve(printers);
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  connectPrinter: function (inner_mac_address) {
    return new Promise(function (resolve, reject) {
      return RNBLEPrinter.connectPrinter(
        inner_mac_address,
        function (printer) {
          return resolve(printer);
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  closeConn: function () {
    return new Promise(function (resolve) {
      RNBLEPrinter.closeConn();
      resolve();
    });
  },
  printText: function (text, opts) {
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      var processedText = textPreprocessingIOS(text, false, false);
      RNBLEPrinter.printRawData(
        processedText.text,
        processedText.opts,
        function (error) {
          return console.warn(error);
        }
      );
    } else {
      RNBLEPrinter.printRawData(textTo64Buffer(text, opts), function (error) {
        return console.warn(error);
      });
    }
  },
  printBill: function (text, opts) {
    var _a, _b;
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      var processedText = textPreprocessingIOS(
        text,
        (_a = opts === null || opts === void 0 ? void 0 : opts.cut) !== null &&
          _a !== void 0
          ? _a
          : true,
        (_b = opts.beep) !== null && _b !== void 0 ? _b : true
      );
      RNBLEPrinter.printRawData(
        processedText.text,
        processedText.opts,
        function (error) {
          return console.warn(error);
        }
      );
    } else {
      RNBLEPrinter.printRawData(billTo64Buffer(text, opts), function (error) {
        return console.warn(error);
      });
    }
  },
  /**
   * image url
   * @param imgUrl
   * @param opts
   */
  printImage: function (imgUrl, opts) {
    var _a, _b;
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      /**
       * just development
       */
      RNBLEPrinter.printImageData(imgUrl, opts, function (error) {
        return console.warn(error);
      });
    } else {
      RNBLEPrinter.printImageData(
        imgUrl,
        (_a = opts === null || opts === void 0 ? void 0 : opts.imageWidth) !==
          null && _a !== void 0
          ? _a
          : 0,
        (_b = opts === null || opts === void 0 ? void 0 : opts.imageHeight) !==
          null && _b !== void 0
          ? _b
          : 0,
        function (error) {
          return console.warn(error);
        }
      );
    }
  },
  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: function (Base64, opts) {
    var _a, _b;
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      /**
       * just development
       */
      RNBLEPrinter.printImageBase64(Base64, opts, function (error) {
        return console.warn(error);
      });
    } else {
      /**
       * just development
       */
      RNBLEPrinter.printImageBase64(
        Base64,
        (_a = opts === null || opts === void 0 ? void 0 : opts.imageWidth) !==
          null && _a !== void 0
          ? _a
          : 0,
        (_b = opts === null || opts === void 0 ? void 0 : opts.imageHeight) !==
          null && _b !== void 0
          ? _b
          : 0,
        function (error) {
          return console.warn(error);
        }
      );
    }
  },
  /**
   * android print with encoder
   * @param text
   */
  printRaw: function (text) {
    if (Platform.OS === "ios") {
      var processedText = textPreprocessingIOS(text, false, false);
      RNBLEPrinter.printRawData(
        processedText.text,
        processedText.opts,
        function (error) {
          return console.warn(error);
        }
      );
    } else {
      RNBLEPrinter.printRawData(text, function (error) {
        return console.warn(error);
      });
    }
  },
  /**
   * `columnWidth`
   * 80mm => 46 character
   * 58mm => 30 character
   */
  printColumnsText: function (
    texts,
    columnWidth,
    columnAlignment,
    columnStyle,
    opts
  ) {
    if (opts === void 0) {
      opts = {};
    }
    var result = processColumnText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle
    );
    if (Platform.OS === "ios") {
      var processedText = textPreprocessingIOS(result, false, false);
      RNBLEPrinter.printRawData(
        processedText.text,
        processedText.opts,
        function (error) {
          return console.warn(error);
        }
      );
    } else {
      RNBLEPrinter.printRawData(textTo64Buffer(result, opts), function (error) {
        return console.warn(error);
      });
    }
  },
};
var NetPrinter = {
  init: function () {
    return new Promise(function (resolve, reject) {
      return RNNetPrinter.init(
        function () {
          return resolve();
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  getDeviceList: function () {
    return new Promise(function (resolve, reject) {
      return RNNetPrinter.getDeviceList(
        function (printers) {
          return resolve(printers);
        },
        function (error) {
          return reject(error);
        }
      );
    });
  },
  connectPrinter: function (host, port, timeout) {
    return new Promise(function (resolve, reject) {
      return __awaiter(void 0, void 0, void 0, function () {
        var error_1;
        return __generator(this, function (_a) {
          switch (_a.label) {
            case 0:
              _a.trys.push([0, 2, , 3]);
              return [4 /*yield*/, connectToHost(host, timeout)];
            case 1:
              _a.sent();
              RNNetPrinter.connectPrinter(
                host,
                port,
                function (printer) {
                  return resolve(printer);
                },
                function (error) {
                  return reject(error);
                }
              );
              return [3 /*break*/, 3];
            case 2:
              error_1 = _a.sent();
              reject(
                (error_1 === null || error_1 === void 0
                  ? void 0
                  : error_1.message) || "Connect to ".concat(host, " fail")
              );
              return [3 /*break*/, 3];
            case 3:
              return [2 /*return*/];
          }
        });
      });
    });
  },
  closeConn: function () {
    return new Promise(function (resolve) {
      RNNetPrinter.closeConn();
      resolve();
    });
  },
  printText: function (text, opts) {
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      var processedText = textPreprocessingIOS(text, false, false);
      RNNetPrinter.printRawData(
        processedText.text,
        processedText.opts,
        function (error) {
          return console.warn(error);
        }
      );
    } else {
      RNNetPrinter.printRawData(textTo64Buffer(text, opts), function (error) {
        return console.warn(error);
      });
    }
  },
  printBill: function (text, opts) {
    var _a, _b;
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      var processedText = textPreprocessingIOS(
        text,
        (_a = opts === null || opts === void 0 ? void 0 : opts.cut) !== null &&
          _a !== void 0
          ? _a
          : true,
        (_b = opts.beep) !== null && _b !== void 0 ? _b : true
      );
      RNNetPrinter.printRawData(
        processedText.text,
        processedText.opts,
        function (error) {
          return console.warn(error);
        }
      );
    } else {
      RNNetPrinter.printRawData(billTo64Buffer(text, opts), function (error) {
        return console.warn(error);
      });
    }
  },
  /**
   * image url
   * @param imgUrl
   * @param opts
   */
  printImage: function (imgUrl, opts) {
    var _a, _b;
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      RNNetPrinter.printImageData(imgUrl, opts, function (error) {
        return console.warn(error);
      });
    } else {
      RNNetPrinter.printImageData(
        imgUrl,
        (_a = opts === null || opts === void 0 ? void 0 : opts.imageWidth) !==
          null && _a !== void 0
          ? _a
          : 0,
        (_b = opts === null || opts === void 0 ? void 0 : opts.imageHeight) !==
          null && _b !== void 0
          ? _b
          : 0,
        function (error) {
          return console.warn(error);
        }
      );
    }
  },
  /**
   * base 64 string
   * @param Base64
   * @param opts
   */
  printImageBase64: function (Base64, opts) {
    var _a, _b;
    if (opts === void 0) {
      opts = {};
    }
    if (Platform.OS === "ios") {
      RNNetPrinter.printImageBase64(Base64, opts, function (error) {
        return console.warn(error);
      });
    } else {
      RNNetPrinter.printImageBase64(
        Base64,
        (_a = opts === null || opts === void 0 ? void 0 : opts.imageWidth) !==
          null && _a !== void 0
          ? _a
          : 0,
        (_b = opts === null || opts === void 0 ? void 0 : opts.imageHeight) !==
          null && _b !== void 0
          ? _b
          : 0,
        function (error) {
          return console.warn(error);
        }
      );
    }
  },
  /**
   * Android print with encoder
   * @param text
   */
  printRaw: function (text) {
    if (Platform.OS === "ios") {
    } else {
      RNNetPrinter.printRawData(text, function (error) {
        return console.warn(error);
      });
    }
  },
  /**
   * `columnWidth`
   * 80mm => 46 character
   * 58mm => 30 character
   */
  printColumnsText: function (
    texts,
    columnWidth,
    columnAlignment,
    columnStyle,
    opts
  ) {
    if (columnStyle === void 0) {
      columnStyle = [];
    }
    if (opts === void 0) {
      opts = {};
    }
    var result = processColumnText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle
    );
    if (Platform.OS === "ios") {
      var processedText = textPreprocessingIOS(result, false, false);
      RNNetPrinter.printRawData(
        processedText.text,
        processedText.opts,
        function (error) {
          return console.warn(error);
        }
      );
    } else {
      RNNetPrinter.printRawData(textTo64Buffer(result, opts), function (error) {
        return console.warn(error);
      });
    }
  },
};
var NetPrinterEventEmitter =
  Platform.OS === "ios"
    ? new NativeEventEmitter(RNNetPrinter)
    : new NativeEventEmitter();
export { COMMANDS, NetPrinter, BLEPrinter, USBPrinter, NetPrinterEventEmitter };
export var RN_THERMAL_RECEIPT_PRINTER_EVENTS;
(function (RN_THERMAL_RECEIPT_PRINTER_EVENTS) {
  RN_THERMAL_RECEIPT_PRINTER_EVENTS["EVENT_NET_PRINTER_SCANNED_SUCCESS"] =
    "scannerResolved";
  RN_THERMAL_RECEIPT_PRINTER_EVENTS["EVENT_NET_PRINTER_SCANNING"] =
    "scannerRunning";
  RN_THERMAL_RECEIPT_PRINTER_EVENTS["EVENT_NET_PRINTER_SCANNED_ERROR"] =
    "registerError";
})(
  RN_THERMAL_RECEIPT_PRINTER_EVENTS || (RN_THERMAL_RECEIPT_PRINTER_EVENTS = {})
);
